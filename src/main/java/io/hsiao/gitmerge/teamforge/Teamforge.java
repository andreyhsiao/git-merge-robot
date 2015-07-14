package io.hsiao.gitmerge.teamforge;

import java.rmi.RemoteException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.collabnet.ce.soap60.webservices.ClientSoapStubFactory;
import com.collabnet.ce.soap60.webservices.cemain.ICollabNetSoap;
import com.collabnet.ce.soap60.webservices.frs.IFrsAppSoap;
import com.collabnet.ce.soap60.webservices.frs.PackageSoapDO;
import com.collabnet.ce.soap60.webservices.scm.IScmAppSoap;
import com.collabnet.ce.soap60.webservices.scm.Repository2SoapDO;

public final class Teamforge {
  private final ICollabNetSoap cemainSoap;
  private final IFrsAppSoap frsAppSoap;
  private final IScmAppSoap scmAppSoap;

  private String username;
  private String sessionKey;

  public Teamforge(final String serverUrl, final int timeoutMs) throws Exception {
    if (serverUrl == null) {
      throw new NullPointerException("argument 'serverUrl' is null");
    }

    cemainSoap = (ICollabNetSoap) ClientSoapStubFactory.getSoapStub(ICollabNetSoap.class, serverUrl, timeoutMs);
    frsAppSoap = (IFrsAppSoap) ClientSoapStubFactory.getSoapStub(IFrsAppSoap.class, serverUrl, timeoutMs);
    scmAppSoap = (IScmAppSoap) ClientSoapStubFactory.getSoapStub(IScmAppSoap.class, serverUrl, timeoutMs);
  }

  public void login(final String username, final String password) throws RemoteException {
    if (username == null) {
      throw new NullPointerException("argument 'username' is null");
    }

    if (password == null) {
      throw new NullPointerException("argument 'password' is null");
    }

    this.username = username;
    sessionKey = cemainSoap.login(username, password);
  }

  public void logoff() throws RemoteException {
    cemainSoap.logoff(username, sessionKey);
  }

  public String setBranchCommitStatus(final String packageId, final boolean isCommitAllowed) throws RemoteException {
    if (packageId == null) {
      throw new NullPointerException("argument 'packageId' is null");
    }

    final PackageSoapDO packageSoapDO = frsAppSoap.getPackageData(sessionKey, packageId);
    String description = packageSoapDO.getDescription();

    final Pattern pattern;
    if (!isCommitAllowed) {
      pattern = Pattern.compile("\\[\\s*version\\s*\\:\\s*(.+?)\\s*\\]", Pattern.CASE_INSENSITIVE);
    }
    else {
      pattern = Pattern.compile("\\[\\s*version\\s*\\:\\s*(.+?)_locked\\s*\\]", Pattern.CASE_INSENSITIVE);
    }

    final Matcher matcher = pattern.matcher(description);
    if (matcher.find()) {
      final String version = matcher.group(1);
      if (!isCommitAllowed) {
        if (!version.endsWith("_locked")) {
          description = matcher.replaceFirst("\\[version\\:" + version + "_locked\\]");
        }
      }
      else {
        description = matcher.replaceFirst("\\[version\\:" + version + "\\]");
      }
    }
    else {
      throw new RuntimeException("[ERROR] failed to set branch commit status [" + packageSoapDO.getTitle() + "]: version info not found");
    }

    packageSoapDO.setDescription(description);
    frsAppSoap.setPackageData(sessionKey, packageSoapDO);

    return description;
  }

  public void setRepositoryCommitIdHook(final String repositoryId, final boolean idRequiredOnCommit) throws RemoteException {
    if (repositoryId == null) {
      throw new NullPointerException("argument 'repositoryId' is null");
    }

    final Repository2SoapDO repository2SoapDO = scmAppSoap.getRepository2DataById(sessionKey, repositoryId);
    repository2SoapDO.setIdRequiredOnCommit(idRequiredOnCommit);
    scmAppSoap.setRepositoryData(sessionKey, repository2SoapDO);
  }
}
