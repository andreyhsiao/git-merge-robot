package io.hsiao.gitmerge.jgit;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.BlameCommand;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CheckoutResult;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.StatusCommand;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

public final class JGit {
  private static final String ENCODING = "UTF-8";

  private final Repository repository;
  private final Git git;
  private final boolean isUseProgressMonitor;

  public JGit(final String gitDir, final String gitProgressMonitor) throws Exception {
    if (gitDir == null) {
      throw new NullPointerException("argument 'gitDir' is null");
    }

    if (gitProgressMonitor == null) {
      throw new NullPointerException("argument 'gitProgressMonitor' is null");
    }

    if (gitProgressMonitor.equalsIgnoreCase("true") || gitProgressMonitor.equalsIgnoreCase("yes")) {
      isUseProgressMonitor = true;
    }
    else {
      isUseProgressMonitor = false;
    }

    repository = new FileRepositoryBuilder().setGitDir(new File(gitDir)).readEnvironment().findGitDir().build();
    git = new Git(repository);
  }

  public boolean isClean() throws Exception {
    final StatusCommand cmdStatus = git.status();

    if (isUseProgressMonitor) {
      cmdStatus.setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)));
    }

    return cmdStatus.call().isClean();
  }

  public File getWorkTree() {
    return repository.getWorkTree();
  }

  public StoredConfig getConfig() {
    return repository.getConfig();
  }

  public void saveConfig(final StoredConfig config) throws Exception {
    if (config == null) {
      throw new NullPointerException("argument 'config' is null");
    }

    config.save();
  }

  public DirCache add(final List<String> filepatterns, final boolean isUpdate) throws Exception {
    final AddCommand cmdAdd = git.add();

    if (filepatterns == null) {
      cmdAdd.addFilepattern(".");
    }
    else {
      for (String filepattern: filepatterns) {
        filepattern = filepattern.trim();
        if (!filepattern.isEmpty()) {
          cmdAdd.addFilepattern(filepattern);
        }
      }
    }

    cmdAdd.setUpdate(isUpdate);

    return cmdAdd.call();
  }

  public BlameResult blame(final String file, final boolean isFollowFileRenames) throws Exception {
    if (file == null) {
      throw new NullPointerException("argument 'file' is null");
    }

    final BlameCommand cmdBlame = git.blame();

    cmdBlame.setFilePath(file);
    cmdBlame.setStartCommit(repository.resolve(Constants.HEAD));
    cmdBlame.setFollowFileRenames(isFollowFileRenames);

    return cmdBlame.call();
  }

  public CheckoutResult checkout(final String branch) throws Exception {
    if (branch == null) {
      throw new NullPointerException("argument 'branch' is null");
    }

    final CheckoutCommand cmdCheckout = git.checkout();

    cmdCheckout.setName(branch);
    cmdCheckout.call();

    return cmdCheckout.getResult();
  }

  public RevCommit commit(final PersonIdent author, final PersonIdent committer, final String message) throws Exception {
    if (message == null) {
      throw new NullPointerException("argument 'message' is null");
    }

    final CommitCommand cmdCommit = git.commit();

    cmdCommit.setAuthor(author);
    cmdCommit.setCommitter(committer);
    cmdCommit.setMessage(message);

    return cmdCommit.call();
  }

  public FetchResult fetch(final String remote, final List<String> refSpecs, final String username, final String password) throws Exception {
    final FetchCommand cmdFetch = git.fetch();
    cmdFetch.setCheckFetchedObjects(true);

    if (isUseProgressMonitor) {
      cmdFetch.setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)));
    }

    if (remote != null) {
      cmdFetch.setRemote(remote);
    }

    if (refSpecs != null) {
      final List<RefSpec> specs = new ArrayList<>();
      for (String refSpec: refSpecs) {
        refSpec = refSpec.trim();
        if (!refSpec.isEmpty()) {
          specs.add(new RefSpec(refSpec));
        }
      }
      cmdFetch.setRefSpecs(specs);
    }

    CredentialsProvider credentialsProvider = null;
    if ((username != null) && (password != null)) {
      credentialsProvider = new UsernamePasswordCredentialsProvider(username, password);
      cmdFetch.setCredentialsProvider(credentialsProvider);
    }

    final FetchResult resFetch = cmdFetch.call();

    if (credentialsProvider != null) {
      ((UsernamePasswordCredentialsProvider) credentialsProvider).clear();
    }

    return resFetch;
  }

  public ObjectId update(final String branch, final String remote) throws Exception {
    if (branch == null) {
      throw new NullPointerException("argument 'branch' is null");
    }

    if (remote == null) {
      throw new NullPointerException("argument 'remote' is null");
    }

    final CheckoutResult resCheckout = checkout(branch);
    if (resCheckout.getStatus() != CheckoutResult.Status.OK) {
      throw new RuntimeException("[ERROR] failed to checkout branch [" + branch + "] [" + resCheckout.getStatus() + "]");
    }

    final MergeCommand cmdMerge = git.merge();
    cmdMerge.include(repository.resolve(remote + "/" + branch));
    cmdMerge.setFastForward(FastForwardMode.FF_ONLY);

    final MergeResult resMerge = cmdMerge.call();
    if (!resMerge.getMergeStatus().isSuccessful()) {
      throw new RuntimeException("[ERROR] failed to update branch [" + branch + "] with remote [" + remote + "] [" + resMerge.getMergeStatus() + "]");
    }

    return resMerge.getNewHead();
  }

  public Iterable<RevCommit> log(final AnyObjectId start, final int maxCount) throws Exception {
    if (start == null) {
      throw new NullPointerException("argument 'start' is null");
    }

    return git.log().add(start).setMaxCount(maxCount).call();
  }

  public String log(final RevCommit revCommit) {
    if (revCommit == null) {
      throw new NullPointerException("argument 'revCommit' is null");
    }

    final StringBuilder sb = new StringBuilder();

    sb.append("Commit: ").append(revCommit.getId().getName()).append("\n");
    sb.append("Author: ").append(revCommit.getAuthorIdent().getName()).append(" <").append(revCommit.getAuthorIdent().getEmailAddress()).append(">\n");
    sb.append("Date:   ").append(revCommit.getAuthorIdent().getWhen()).append("\n\n");
    sb.append(revCommit.getFullMessage()).append("\n");

    return sb.toString();
  }

  public String note(final RevCommit revCommit) throws Exception {
    if (revCommit == null) {
      throw new NullPointerException("argument 'revCommit' is null");
    }

    final Note note = git.notesShow().setObjectId(revCommit).call();

    if (note != null) {
      try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
        repository.open(note.getData()).copyTo(os);
        return os.toString(ENCODING);
      }
    }

    return "";
  }

  public String logWithNotes(final RevCommit revCommit) throws Exception {
    if (revCommit == null) {
      throw new NullPointerException("argument 'revCommit' is null");
    }

    final StringBuilder sb = new StringBuilder();

    sb.append(log(revCommit));

    final String note = note(revCommit);
    if (!note.isEmpty()) {
      sb.append("\nNotes:\n").append(note);
    }

    return sb.toString();
  }

  public MergeResult merge(final String from, final String to, final String message) throws Exception {
    if (from == null) {
      throw new NullPointerException("argument 'from' is null");
    }

    if (to == null) {
      throw new NullPointerException("argument 'to' is null");
    }

    if (message == null) {
      throw new NullPointerException("argument 'message' is null");
    }

    final CheckoutResult resCheckout = checkout(to);
    if (resCheckout.getStatus() != CheckoutResult.Status.OK) {
      throw new RuntimeException("[ERROR] failed to checkout branch [" + to + "] [" + resCheckout.getStatus() + "]");
    }
    else {
      System.out.println(">> Checked out branch [" + to + "] ...\n");
    }

    final MergeCommand cmdMerge = git.merge();

    cmdMerge.include(resolve(from));

    cmdMerge.setCommit(false);
    cmdMerge.setMessage(message);
    cmdMerge.setFastForward(FastForwardMode.NO_FF);
    cmdMerge.setStrategy(MergeStrategy.RECURSIVE);

    System.out.println(">> Starting the merge, please be patient ...\n");
    return cmdMerge.call();
  }

  public Status status() throws Exception {
    final StatusCommand cmdStatus = git.status();

    if (isUseProgressMonitor) {
      cmdStatus.setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)));
    }

    return cmdStatus.call();
  }

  private String getSvnRevision(final String expression) throws Exception {
    if (expression == null) {
      throw new NullPointerException("argument 'expression' is null");
    }

    final String notes = note(log(repository.resolve(expression), 1).iterator().next());

    final Pattern pattern = Pattern.compile("\\A\\s*(r\\d+)\\s+.+", Pattern.CASE_INSENSITIVE);
    final Matcher matcher = pattern.matcher(notes);

    if (matcher.matches()) {
      return matcher.group(1);
    }

    throw new RuntimeException("[ERROR] failed to get SVN revision for [" + expression + "]");
  }

  public Map<String, String> parse(final String expression, final String remote) throws Exception {
    if (expression == null) {
      throw new NullPointerException("argument 'expression' is null");
    }

    if (remote == null) {
      throw new NullPointerException("argument 'remote' is null");
    }

    final Map<String, String> map = new HashMap<>();

    final Pattern pattern = Pattern.compile("\\A\\s*(\\S+?)\\s*\\:\\s*(svn|git)\\s*\\:(\\S+)\\s*\\Z", Pattern.CASE_INSENSITIVE);
    final Matcher matcher = pattern.matcher(expression);

    if (!matcher.matches()) {
      map.put("mergeFromBranch", expression);
      map.put("mergeFromRevision", getSvnRevision(remote + "/" + expression));
      return map;
    }

    String branch = matcher.group(1);
    String type = matcher.group(2);
    String rev = matcher.group(3);

    map.put("mergeFromBranch", branch);

    if (type.equalsIgnoreCase("git")) {
      final ObjectId objectId = repository.resolve(rev);

      if (objectId == null) {
        throw new RuntimeException("[ERROR] invalid git revision expression [" + rev + "]");
      }

      map.put("mergeFromObjectId", objectId.getName());
      map.put("mergeFromRevision", getSvnRevision(rev));
      return map;
    }

    rev = rev.toLowerCase();
    if (!rev.startsWith("r")) {
      rev = "r" + rev;
    }
    map.put("mergeFromRevision", rev);

    final List<Note> notes = git.notesList().call();

    if (notes == null) {
      throw new RuntimeException("[ERROR] failed to get object id for SVN revision [" + rev + "]");
    }

    for (final Note note: notes) {
      try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
        repository.open(note.getData()).copyTo(os);

        final String data = os.toString(ENCODING);
        if (data.matches("\\A\\s*" + rev + "\\s*.+\\Z")) {
          map.put("mergeFromObjectId", note.getName());
          return map;
        }
      }
    }

    throw new RuntimeException("[ERROR] failed to get object id for SVN revision [" + rev + "]");
  }

  public ObjectId resolve(final String expression) throws Exception {
    if (expression == null) {
      throw new NullPointerException("argument 'expression' is null");
    }

    final ObjectId objectId = repository.resolve(expression);

    if (objectId == null) {
      throw new RuntimeException("[ERROR] failed to resolve [" + expression + "] to object id");
    }

    return objectId;
  }

  public String readMergeCommitMsg() throws Exception {
    return repository.readMergeCommitMsg();
  }

  public Iterable<PushResult> push(final String branch, final String remote, final String username, final String password) throws Exception {
    if (branch == null) {
      throw new NullPointerException("argument 'branch' is null");
    }

    final PushCommand cmdPush = git.push();

    if (remote != null) {
      cmdPush.setRemote(remote);
    }

    cmdPush.setRefSpecs(new RefSpec(branch + ":" + branch));
    cmdPush.setForce(false);
    cmdPush.setOutputStream(System.out);

    if (isUseProgressMonitor) {
      cmdPush.setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)));
    }

    CredentialsProvider credentialsProvider = null;
    if ((username != null) && (password != null)) {
      credentialsProvider = new UsernamePasswordCredentialsProvider(username, password);
      cmdPush.setCredentialsProvider(credentialsProvider);
    }

    final Iterable<PushResult> resPushes = cmdPush.call();

    if (credentialsProvider != null) {
      ((UsernamePasswordCredentialsProvider) credentialsProvider).clear();
    }

    return resPushes;
  }

  public void close() {
    repository.close();
    git.close();
  }

  public static PersonIdent getPersonIdent(final String name, final String email) {
    if ((name == null) || (email == null)) {
      return null;
    }

    return new PersonIdent(name, email);
  }

  public static String shortenRefName(final String name) {
    if (name == null) {
      throw new NullPointerException("argument 'name' is null");
    }

    return Repository.shortenRefName(name);
  }
}
