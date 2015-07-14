package io.hsiao.gitmerge;

import io.hsiao.gitmerge.jgit.JGit;
import io.hsiao.gitmerge.mail.Mail;
import io.hsiao.gitmerge.teamforge.Teamforge;
import io.hsiao.gitmerge.utils.CommonUtils;
import io.hsiao.gitmerge.utils.FileUtils;
import io.hsiao.gitmerge.utils.StringUtils;
import io.hsiao.gitmerge.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import org.eclipse.jgit.api.CheckoutResult;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.IndexDiff.StageState;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.merge.ResolveMerger.MergeFailureReason;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.TrackingRefUpdate;

public final class Robot {
  private static final int OUTPUT_WIDTH = 100;
  private static final String BLAME_FILE_NAME = "blame.zip";
  private static final String CONFIG_FILE_NAME = "config.properties";
  private static final String ENCODING = "UTF-8";

  private final JGit jgit;
  private final Properties props;
  private final Teamforge teamforge;

  private final File tmpDir;
  private final Map<String, StageState> conflicts;

  private String mergeFromBranch;
  private String mergeFromObjectId;
  private String mergeFromRevision;

  public Robot() throws Exception {
    props = CommonUtils.loadProperties(CONFIG_FILE_NAME);

    jgit = new JGit(CommonUtils.getProperty(props, "git.repo.dir", false), CommonUtils.getProperty(props, "git.progress.monitor", false));

    if (props.containsKey("ctf.server.url") && props.containsKey("ctf.username") && props.containsKey("ctf.password")) {
      teamforge = new Teamforge(CommonUtils.getProperty(props, "ctf.server.url", false), 60 * 1000);
    }
    else {
      teamforge = null;
    }

    tmpDir = Files.createTempDirectory(null).toFile();
    conflicts = new TreeMap<>();
  }

  public void powerOn() {
    System.out.println(StringUtils.prettyFormat("=", OUTPUT_WIDTH, "Git Merge Robot [started]"));
  }

  public void doWork() throws Exception {
    final String mergeFrom = CommonUtils.getSystemProperty("mergeFrom", false);
    final String mergeTo = CommonUtils.getSystemProperty("mergeTo", false);
    final String message = CommonUtils.getSystemProperty("message", false);

    final String remote = CommonUtils.getProperty(props, "git.remote", false);

    // checking repository cleanliness
    System.out.println("==> Checking git repository cleanliness\n");
    if (!jgit.isClean()) {
      throw new RuntimeException("[ERROR] git repository is NOT clean [aborted]");
    }

    // locking 'to' branch
    System.out.println("==> Locking branch [" + mergeTo + "]\n");
    setBranchCommitStatus(mergeTo, false);

    // fetching from remote
    System.out.println("==> Fetching from remote [" + remote + "]\n");
    doFetch(remote);

    // parsing 'mergeFrom' expression
    System.out.println("==> Parsing 'mergeFrom' expression\n");
    doParse(mergeFrom, remote);

    // updating branches with remote
    System.out.println("==> Updating branches with remote [" + remote + "]\n");
    System.out.println("==> This may take a while, please be patient ...\n");
    doUpdate(mergeFromBranch, remote);
    doUpdate(mergeTo, remote);

    // merging branches (or specific commits)
    if (mergeFromObjectId == null) {
      System.out.println("==> Merging branch [" + mergeFromBranch + "] to [" + mergeTo + "]\n");
      doForkMerge(mergeFromBranch, mergeTo, message);
    }
    else {
      System.out.println("==> Merging commit [" + mergeFromObjectId + "] to [" + mergeTo + "]\n");
      doForkMerge(mergeFromObjectId, mergeTo, message);
    }

    // disable repository commit id hook
    // System.out.println("==> Disabling SVN repository commit id hook\n");
    // setRepositoryCommitIdHook(false);

    // pushing to remote
    System.out.println("==> Pushing to remote [" + remote + "]\n");
    doForkPush(mergeTo, remote);

    // enable repository commit id hook
    // System.out.println("==> Enabling SVN repository commit id hook\n");
    // setRepositoryCommitIdHook(true);

    // unlocking 'to' branch
    System.out.println("==> Unlocking branch [" + mergeTo + "]\n");
    setBranchCommitStatus(mergeTo, true);

    // blaming on conflicting files
    System.out.println("==> Blaming on conflicting files\n");
    doBlame();

    // sending summary mail
    System.out.println("==> Sending out summary mail\n");
    doSendMail();

    jgit.close();
  }

  public void powerOff() {
    System.out.println(StringUtils.prettyFormat("=", OUTPUT_WIDTH, "Git Merge Robot [ended]"));
  }

  public static void main(String[] args) throws Exception {
    final Robot robot = new Robot();
    robot.powerOn();
    robot.doWork();
    robot.powerOff();
  }

  private void setBranchCommitStatus(final String branch, final boolean isCommitAllowed) throws Exception {
    if (branch == null) {
      throw new NullPointerException("argument 'branch' is null");
    }

    if (props.containsKey(branch)) {
      if (teamforge == null) {
        throw new RuntimeException("[ERROR] failed to " + (!isCommitAllowed ? "lock" : "unlock") + " branch [" + branch + "]: teamforge may not be configured properly");
      }

      teamforge.login(CommonUtils.getProperty(props, "ctf.username", false), CommonUtils.getProperty(props, "ctf.password", false));

      final String[] packageIds = CommonUtils.getProperty(props, branch, false).split(",");
      final StringBuilder sb = new StringBuilder();

      for (String packageId: packageIds) {
        packageId = packageId.trim();
        if (!packageId.isEmpty()) {
          sb.append(teamforge.setBranchCommitStatus(packageId, isCommitAllowed)).append("\n");
        }
      }

      System.out.println(StringUtils.prettyFormat("-", OUTPUT_WIDTH, (!isCommitAllowed ? "Locked" : "Unlocked") + " branch [" + branch + "]", sb.toString().trim()));
      teamforge.logoff();
    }
    else {
      System.out.println(StringUtils.prettyFormat("*", OUTPUT_WIDTH, "[WARN] " + (!isCommitAllowed ? "Locking" : "Unlocking") + " branch [" + branch + "] skipped"));
    }
  }

  @SuppressWarnings("unused")
  private void setRepositoryCommitIdHook(final boolean idRequiredOnCommit) throws Exception {
    if (props.containsKey("svn.repo.id")) {
      final String repositoryId = CommonUtils.getProperty(props, "svn.repo.id", false);

      if (teamforge == null) {
        throw new RuntimeException("[ERROR] failed to set repository commit id hook [" + repositoryId + "]: teamforge may not be configured properly");
      }

      teamforge.login(CommonUtils.getProperty(props, "ctf.username", false), CommonUtils.getProperty(props, "ctf.password", false));

      teamforge.setRepositoryCommitIdHook(repositoryId, idRequiredOnCommit);

      System.out.println(StringUtils.prettyFormat("-", OUTPUT_WIDTH, "Turned [" + (idRequiredOnCommit ? "on" : "off") + "] commit id hook for repository [" + repositoryId + "]"));
      teamforge.logoff();
    }
    else {
      System.out.println(StringUtils.prettyFormat("*", OUTPUT_WIDTH, "[WARN] set repository commit id hook skipped"));
    }
  }

  private void doFetch(final String remote) throws Exception {
    if (remote == null) {
      throw new NullPointerException("argument 'remote' is null");
    }

    String username = null;
    String password = null;
    if (props.containsKey("git.username") && props.containsKey("git.password")) {
      username = CommonUtils.getProperty(props, "git.username", false);
      password = CommonUtils.getProperty(props, "git.password", false);
    }

    final String[] refSpecs = CommonUtils.getProperty(props, "git.fetch.refspecs", false).split(",");

    final FetchResult fetchResult = jgit.fetch(remote, Arrays.asList(refSpecs), username, password);

    final StringBuilder sb = new StringBuilder();

    for (final TrackingRefUpdate trackingRefUpdate: fetchResult.getTrackingRefUpdates()) {
      sb.append(String.format("%-20s %s..%s %25s", "[" + trackingRefUpdate.getResult().toString().toLowerCase() + "]",
          trackingRefUpdate.getOldObjectId().abbreviate(7).name(),
          trackingRefUpdate.getNewObjectId().abbreviate(7).name(),
          trackingRefUpdate.getRemoteName())).append("\n");
    }

    if (sb.length() == 0) {
      sb.append("[Already up to date] nothing to do").append("\n");
    }

    System.out.println(StringUtils.prettyFormat("+", OUTPUT_WIDTH, "Fetched from remote [" + remote + "]", sb.toString().trim()));
  }

  private void doParse(final String expression, final String remote) throws Exception {
    if (expression == null) {
      throw new NullPointerException("argument 'expression' is null");
    }

    if (remote == null) {
      throw new NullPointerException("argument 'remote' is null");
    }

    final Map<String, String> map = jgit.parse(expression, remote);

    mergeFromBranch = map.get("mergeFromBranch");
    mergeFromObjectId = map.get("mergeFromObjectId");
    mergeFromRevision = map.get("mergeFromRevision");

    final StringBuilder sb = new StringBuilder();

    if (mergeFromObjectId == null) {
      sb.append("Branch [" + mergeFromBranch + " (" + mergeFromRevision + ")] will be merged").append("\n");
    }
    else {
      sb.append("Commit [" + mergeFromObjectId + " (" + mergeFromRevision + ")] will be merged").append("\n\n");
      sb.append(jgit.logWithNotes(jgit.log(jgit.resolve(mergeFromObjectId), 1).iterator().next())).append("\n");
    }

    System.out.println(StringUtils.prettyFormat("-", OUTPUT_WIDTH, "Parsed 'mergeFrom' expression", sb.toString().trim()));
  }

  private void doUpdate(final String branch, final String remote) throws Exception {
    if (branch == null) {
      throw new NullPointerException("argument 'branch' is null");
    }

    if (remote == null) {
      throw new NullPointerException("argument 'remote' is null");
    }

    System.out.println(">> Updating local branch [" + branch + "]\n");
    final RevCommit newHead = jgit.log(jgit.update(branch, remote), 1).iterator().next();

    System.out.println(StringUtils.prettyFormat("-", OUTPUT_WIDTH, "Branch [" + branch + "] updated", jgit.logWithNotes(newHead).trim()));
  }

  // Until Bug 471845 is fixed, using JGit for merge should be avoided
  // URL: https://bugs.eclipse.org/bugs/show_bug.cgi?id=471845
  @SuppressWarnings("unused")
  private Map<String, int[][]> doMerge(final String from, final String to, final String message) throws Exception {
    if (from == null) {
      throw new NullPointerException("argument 'from' is null");
    }

    if (to == null) {
      throw new NullPointerException("argument 'to' is null");
    }

    if (message == null) {
      throw new NullPointerException("argument 'message' is null");
    }

    final MergeResult resMerge = jgit.merge(from, to, message);
    final MergeStatus stsMerge = resMerge.getMergeStatus();

    String base = ">>> No common merge base";
    if (resMerge.getBase() != null) {
      base = ">>> Merge Base:\n\n" + jgit.logWithNotes(jgit.log(resMerge.getBase(), 1).iterator().next());
    }

    Map<String, int[][]> conflicts = null;

    final StringBuilder sb = new StringBuilder();

    // ABORTED, CHECKOUT_CONFLICT, CONFLICTING, FAILED, NOT_SUPPORTED
    if (!stsMerge.isSuccessful()) {
      if (stsMerge == MergeStatus.CHECKOUT_CONFLICT) {
        final List<String> checkoutConflicts = resMerge.getCheckoutConflicts();

        if (checkoutConflicts != null) {
          for (final String checkoutConflict: checkoutConflicts) {
            sb.append(checkoutConflict).append("\n");
          }
        }
      }

      if (stsMerge == MergeStatus.CONFLICTING) {
        conflicts = resMerge.getConflicts();
      }

      if (stsMerge == MergeStatus.FAILED) {
        final Map<String, MergeFailureReason> failingPaths = resMerge.getFailingPaths();

        if (failingPaths != null) {
          for (final Map.Entry<String, MergeFailureReason> failingPath: failingPaths.entrySet()) {
            sb.append(failingPath.getKey()).append(": ").append(failingPath.getValue().toString()).append("\n");
          }
        }
      }

      if (stsMerge != MergeStatus.CONFLICTING) {
        System.out.println(StringUtils.prettyFormat("*", OUTPUT_WIDTH, "Merge FAILURE [" + stsMerge.toString() + "]", sb.toString().trim(), base, resMerge.toString()));
        throw new RuntimeException("[ERROR] failed to merge [" + from + "] to [" + to + "] [" + stsMerge.toString() + "]");
      }
    }

    jgit.add(null, false);

    String name = null;
    String email = null;
    if (props.containsKey("git.username") && props.containsKey("git.email")) {
      name = CommonUtils.getProperty(props, "git.username", false);
      email = CommonUtils.getProperty(props, "git.email", false);
    }

    final String newHead = ">>> Merge Result:\n\n" + jgit.logWithNotes(jgit.commit(JGit.getPersonIdent(name, email), JGit.getPersonIdent(name, email), jgit.readMergeCommitMsg()));

    System.out.println(StringUtils.prettyFormat("+", OUTPUT_WIDTH, "Merge SUCCESS [" + stsMerge.toString() + "]", base, newHead, resMerge.toString()));

    return conflicts;
  }

  private void doForkMerge(final String from, final String to, final String message) throws Exception {
    if (from == null) {
      throw new NullPointerException("argument 'from' is null");
    }

    if (to == null) {
      throw new NullPointerException("argument 'to' is null");
    }

    if (message == null) {
      throw new NullPointerException("argument 'message' is null");
    }

    final CheckoutResult resCheckout = jgit.checkout(to);
    if (resCheckout.getStatus() != CheckoutResult.Status.OK) {
      throw new RuntimeException("[ERROR] failed to checkout branch [" + to + "] [" + resCheckout.getStatus() + "]");
    }
    else {
      System.out.println(">> Checked out branch [" + to + "] ...\n");
    }

    System.out.println(">> Starting the merge, please be patient ...\n");

    final StoredConfig config = jgit.getConfig();
    config.setInt("merge", null, "verbosity", 0);
    jgit.saveConfig(config);

    String cmd = "git merge -s recursive -Xignore-all-space --no-ff --no-commit " + from;
    Process process = Runtime.getRuntime().exec(cmd, null, jgit.getWorkTree());

    final InputStream prOut = process.getInputStream();
    final Thread stdoutDrainer = new Thread() {
      @Override
      public void run()
      {
        try
        {
          int ch;
          do
          {
            ch = prOut.read();
            if (ch >= 0) {
              System.out.print((char) ch);
            }
          }
          while (ch >= 0);
        }
        catch (IOException ex)
        {
          // ex.printStackTrace();
        }
      }
    };

    final InputStream prErr = process.getErrorStream();
    final Thread stderrDrainer = new Thread() {
      @Override
      public void run()
      {
        try
        {
          int ch;
          do
          {
            ch = prErr.read();
            if (ch >= 0) {
              prErr.skip(prErr.available());
            }
          }
          while (ch >= 0);
        }
        catch (IOException ex)
        {
          // ex.printStackTrace();
        }
      }
    };

    stdoutDrainer.start();
    stderrDrainer.start();

    int retValue = process.waitFor();

    try {
      prOut.close();
      prErr.close();
    }
    catch (IOException ex) {
      // ex.printStackTrace();
    }

    System.out.println();

    String commitMessage = message.replaceAll("\\%from", mergeFromBranch).replaceAll("\\%to", to).replaceAll("\\%rev", mergeFromRevision);

    if (retValue == 0) {
      System.out.println(StringUtils.prettyFormat("+", OUTPUT_WIDTH, "Merge Completed [Success]"));
    }
    else if (retValue == 1) {
      System.out.println("==> Running git status to get conflict list\n");
      final Status status = jgit.status();

      conflicts.putAll(status.getConflictingStageState());

      final StringBuilder sb = new StringBuilder();

      for (final Map.Entry<String, StageState> conflict: conflicts.entrySet()) {
        sb.append(String.format("%-20s%s", conflict.getValue().toString(), conflict.getKey())).append("\n");
      }

      commitMessage = new StringBuilder().append(commitMessage).append("\n\n").append("Conflicts:\n\n").append(sb.toString().trim()).toString();
      System.out.println(StringUtils.prettyFormat("+", OUTPUT_WIDTH, "Merge Completed [Conflicting]", sb.toString().trim()));
    }
    else {
      throw new RuntimeException("[ERROR] failed to merge [" + from + "] to [" + to + "] [Unexpected errors occurred (" + retValue + ")]");
    }

    config.unset("merge", null, "verbosity");
    jgit.saveConfig(config);

    System.out.println("==> Adding uncommitted changes to the index\n");
    cmd = "git add .";
    process = Runtime.getRuntime().exec(cmd, null, jgit.getWorkTree());
    retValue = process.waitFor();

    if (retValue != 0) {
      throw new RuntimeException("[ERROR] failed to add uncommitted changes to index");
    }

    System.out.println("==> Committing the merge changes\n");
    String name = null;
    String email = null;
    if (props.containsKey("git.username") && props.containsKey("git.email")) {
      name = CommonUtils.getProperty(props, "git.username", false);
      email = CommonUtils.getProperty(props, "git.email", false);
    }

    final String newHead = new StringBuilder().append(">> Merge Result (the merge commit):\n\n")
        .append(jgit.logWithNotes(jgit.commit(JGit.getPersonIdent(name, email), JGit.getPersonIdent(name, email), commitMessage))).toString();

    System.out.println(StringUtils.prettyFormat("-", OUTPUT_WIDTH, newHead.trim()));

    System.out.println("==> Checking git repository cleanliness\n");
    if (!jgit.isClean()) {
      throw new RuntimeException("[ERROR] after committing all merge changes, the repository should back to clean");
    }
  }

  @SuppressWarnings("unused")
  private void doPush(final String branch, final String remote) throws Exception {
    if (branch == null) {
      throw new NullPointerException("argument 'branch' is null");
    }

    if (remote == null) {
      throw new NullPointerException("argument 'remote' is null");
    }

    String username = null;
    String password = null;
    if (props.containsKey("git.username") && props.containsKey("git.password")) {
      username = CommonUtils.getProperty(props, "git.username", false);
      password = CommonUtils.getProperty(props, "git.password", false);
    }

    final StringBuilder sb = new StringBuilder();

    final Iterable<PushResult> resPushes = jgit.push(branch, remote, username, password);
    for (final PushResult resPush: resPushes) {
      final Collection<RemoteRefUpdate> remoteUpdates = resPush.getRemoteUpdates();

      for (final RemoteRefUpdate remoteUpdate: remoteUpdates) {
        final org.eclipse.jgit.transport.RemoteRefUpdate.Status remoteUpdateStatus = remoteUpdate.getStatus();

        if ((remoteUpdateStatus != org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK) &&
            (remoteUpdateStatus != org.eclipse.jgit.transport.RemoteRefUpdate.Status.UP_TO_DATE)) {
          throw new RuntimeException("[ERROR] failed to push [" + branch + "] to [" + remote + "] [" + remoteUpdateStatus + "]");
        }

        sb.append(String.format("%15s -> %-25s [%s]", JGit.shortenRefName(remoteUpdate.getSrcRef()),
            JGit.shortenRefName(remoteUpdate.getRemoteName()) + " (" + remoteUpdate.getNewObjectId().abbreviate(7).name() + ")",
            remoteUpdateStatus.toString())).append("\n");
      }
    }

    System.out.println(StringUtils.prettyFormat("+", OUTPUT_WIDTH, "Pushed [" + branch + "] to [" + remote + "]", sb.toString().trim()));
  }

  private void doForkPush(final String branch, final String remote) throws Exception {
    if (branch == null) {
      throw new NullPointerException("argument 'branch' is null");
    }

    if (remote == null) {
      throw new NullPointerException("argument 'remote' is null");
    }

    System.out.println(">> Starting the push, please be patient ...\n");

    final String cmd = String.format("git push -v %s %s:%s", remote, branch, branch);
    final Process process = Runtime.getRuntime().exec(cmd, null, jgit.getWorkTree());

    final InputStream prOut = process.getInputStream();
    final Thread stdoutDrainer = new Thread() {
      @Override
      public void run()
      {
        try
        {
          int ch;
          do
          {
            ch = prOut.read();
            if (ch >= 0) {
              System.out.print((char) ch);
            }
          }
          while (ch >= 0);
        }
        catch (IOException ex)
        {
          // ex.printStackTrace();
        }
      }
    };

    final InputStream prErr = process.getErrorStream();
    final Thread stderrDrainer = new Thread() {
      @Override
      public void run()
      {
        try
        {
          int ch;
          do
          {
            ch = prErr.read();
            if (ch >= 0) {
              System.out.print((char) ch);
            }
          }
          while (ch >= 0);
        }
        catch (IOException ex)
        {
          // ex.printStackTrace();
        }
      }
    };

    stdoutDrainer.start();
    stderrDrainer.start();

    final int retValue = process.waitFor();

    try {
      prOut.close();
      prErr.close();
    }
    catch (IOException ex) {
      // ex.printStackTrace();
    }

    System.out.println();

    if (retValue != 0) {
      throw new RuntimeException("[ERROR] failed to push branch [" + branch + "] to remote [" + remote + "] [Unexpected errors occurred (" + retValue + ")]");
    }
  }

  private boolean isBlameExcluded(String file) {
    if (file == null) {
      throw new NullPointerException("argument 'file' is null");
    }

    final String[] excludes = CommonUtils.getProperty(props, "git.blame.excludes", false).split(",");

    file = file.toLowerCase();
    for (String exclude: excludes) {
      exclude = exclude.toLowerCase().trim();

      if (exclude.isEmpty()) {
        continue;
      }

      if (file.endsWith("." + exclude)) {
        return true;
      }
    }

    return false;
  }

  private void doBlame() throws Exception {
    final String skipBlame = CommonUtils.getProperty(props, "git.blame.skip", false);
    if (skipBlame.equalsIgnoreCase("true") || skipBlame.equalsIgnoreCase("yes")) {
      System.out.println(StringUtils.prettyFormat("*", OUTPUT_WIDTH, "[WARN] Blaming on conflicting files skipped"));
      return;
    }

    if (conflicts.isEmpty()) {
      return;
    }

    final StringBuilder blamed = new StringBuilder();

    final String tmpBlameDir = tmpDir.toString() + File.separator + "blame";

    for (final Map.Entry<String, StageState> conflict: conflicts.entrySet()) {
      final String conflictFile = conflict.getKey();
      final StageState conflictState = conflict.getValue();

      if (conflictFile.equalsIgnoreCase(Constants.DOT_GIT_ATTRIBUTES) || conflictFile.equalsIgnoreCase(Constants.DOT_GIT_IGNORE)) {
        continue;
      }

      if (isBlameExcluded(conflictFile)) {
        continue;
      }

      if ((conflictState != StageState.BOTH_ADDED) && (conflictState != StageState.BOTH_MODIFIED)) {
        continue;
      }

      final String dirname = new File(conflictFile).getParent();
      if (dirname != null) {
        FileUtils.mkdir(new File(tmpBlameDir + File.separator + dirname));
      }

      final Format formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss ZZZZ");

      final BlameResult resBlame = jgit.blame(conflictFile, true);
      final RawText resultContents = resBlame.getResultContents();
      final int lines = resultContents.size();

      final PrintWriter writer = new PrintWriter(tmpBlameDir + File.separator + conflictFile);

      int maxAuthorLength = 0;
      for (int idx = 0; idx < lines; ++idx) {
        final int authorLength = resBlame.getSourceAuthor(idx).getName().length();
        if (authorLength > maxAuthorLength) {
          maxAuthorLength = authorLength;
        }
      }

      for (int idx = 0; idx < lines; ++idx) {
        final StringBuilder sb = new StringBuilder();

        sb.append(resBlame.getSourceCommit(idx).abbreviate(7).name()).append("  ");
        sb.append(String.format("%-" + maxAuthorLength + "s", resBlame.getSourceAuthor(idx).getName())).append("  ");
        sb.append(formatter.format(resBlame.getSourceAuthor(idx).getWhen())).append("  ");
        sb.append(String.format("%" + (Integer.toString(lines).length() * 2 + 2) + "s)", resBlame.getSourceLine(idx) + ":" + idx)).append("  ");
        sb.append(resultContents.getString(idx));

        writer.println(sb.toString());
      }

      writer.close();

      blamed.append(conflictFile).append("\n");
    }

    ZipUtils.pack(new File(tmpBlameDir), new File(tmpDir.toString() + File.separator + BLAME_FILE_NAME), false);

    System.out.println(StringUtils.prettyFormat("-", OUTPUT_WIDTH, "Blamed on conflicting files", blamed.toString().trim()));
  }

  private void doSendMail() throws Exception {
    final String skipMail = CommonUtils.getProperty(props, "mail.skip", false);
    if (skipMail.equalsIgnoreCase("true") || skipMail.equalsIgnoreCase("yes")) {
      System.out.println(StringUtils.prettyFormat("*", OUTPUT_WIDTH, "[WARN] Sending summary mail skipped"));
      return;
    }

    final String[] mailTos = CommonUtils.getSystemProperty("mailTo", false).split(",");

    final String smtpHost = CommonUtils.getProperty(props, "mail.smtp.host", false);
    final String smtpPort = CommonUtils.getProperty(props, "mail.smtp.port", false);

    String username = null;
    String password = null;
    if (props.containsKey("mail.username") && props.containsKey("mail.password")) {
      username = CommonUtils.getProperty(props, "mail.username", false);
      password = CommonUtils.getProperty(props, "mail.password", false);
    }

    String domain = null;
    if (props.containsKey("mail.default.domain")) {
      domain = CommonUtils.getProperty(props, "mail.default.domain", false);
    }

    final Mail mail = new Mail(Mail.getProperties(smtpHost, smtpPort));

    if ((username != null) && (domain != null)) {
      mail.setFrom(Mail.getMailAddress(username, domain));
    }
    mail.setSubject("Git Merge Robot - Summary", ENCODING);
    mail.setSentDate(new Date());

    final StringBuilder sb = new StringBuilder();

    sb.append("<head>");
    sb.append("<meta charset=\"").append(ENCODING).append("\"/>");
    sb.append("<style>");
    sb.append("p {font-family:Arial; font-size:11pt; color:black;}");
    sb.append("table,td {font-family:Arial; font-size:10pt; border:1px solid black; border-collapse:collapse; margin:1pt;}");
    sb.append("p.signature1 {font-family:Tahoma Arial; font-size:10pt; color:gray; margin-bottom:0px;}");
    sb.append("p.signature2 {font-family:Tahoma Arial; font-size:11pt; color:gray; margin-top:5px; font-weight:bold;}");
    sb.append("</style>");
    sb.append("</head>");

    sb.append("<body>");
    sb.append("<p>Dear <span style=\"font-style:italic;\">Human</span></p>");
    if (conflicts.isEmpty()) {
      sb.append("<p>Merge completed <span style=\"color:green; font-weight:bold;\">successfully</span> without conflicts.</p>");
      sb.append("<p>Please go ahead and share the exciting news with project team members.</p>");
    }
    else {
      sb.append("<p>Merge completed with <span style=\"color:red; font-weight:bold;\">conflicts</span>, please check.</p>");
      sb.append("<table>");
      for (final Map.Entry<String, StageState> conflict: conflicts.entrySet()) {
        sb.append("<tr>");
        sb.append("<td style=\"text-align:center;\">").append(conflict.getValue().toString()).append("</td>");
        sb.append("<td>").append(conflict.getKey()).append("</td>");
        sb.append("</tr>");
      }
      sb.append("</table>");
    }

    if (Files.exists(new File(tmpDir.toString() + File.separator + BLAME_FILE_NAME).toPath())) {
      sb.append("<p>Please refer to blame information attached.</p>");
      mail.attachFile(tmpDir.toString() + File.separator + BLAME_FILE_NAME);
    }

    sb.append("<p class=\"signature1\">Best Regards</p>");
    sb.append("<p class=\"signature2\">Git Merge Robot</p>");
    sb.append("</body>");

    mail.setContent(sb.toString(), "text/html");
    mail.setRecipients(Mail.RECIPIENT_TYPE_TO, Arrays.asList(mailTos), domain);
    mail.send(username, password);
  }
}
