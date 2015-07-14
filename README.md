Git Merge Robot
===============

![Build Status](https://img.shields.io/badge/build-success-brightgreen.svg)
![JGit Version](https://img.shields.io/badge/jgit-v4.0.1-red.svg)
![Java Version](https://img.shields.io/badge/java-v1.7-orange.svg)

Purposes:
---------
Let robot do the work so that you can  
  
- relax and taste a cup of coffee :coffee:  
- potentially make your clients and partners happy  
- avoid humiliating human mistakes  

Requirements:
-------------
* JDK 1.7 or above
* Git executable must be available in the PATH

Things the robot will do:
-------------------------
01. Checking repository cleanliness before merge
02. Locking merge destination **branch**
03. Fetching new objects from remote
04. Parsing merge source **expression**, valid expression examples:
  - branch-name, eg: dev
  - branch-name:svn:revision, eg: dev:svn:r12306
  - branch-name:git:revision, eg: dev:git:4dbc7c8a297d
05. Updating local branches with remote tracking branches
06. Merging source **expression** to destination **branch**
  - **Until [bug 471845](https://bugs.eclipse.org/bugs/show_bug.cgi?id=471845 "Go to issue tracker") is fixed, using JGit for merge should be avoided**
  - **Since git v2.3.0, merge conflicts will be [commented out](http://comments.gmane.org/gmane.comp.version-control.git/273390 "Go to mail archive") by default in the merge  
       commit message, so the robot will generate his own instead**
  - Commit message supports arguments (%from, %to, %rev)
07. Checking repository cleanliness after merge
08. Pushing new objects to remote
  - **Since JGit doesn't fully support hooks as of now, pushing has to be delegated to Git client - [bug 299315](https://bugs.eclipse.org/bugs/show_bug.cgi?id=299315 "Go to issue tracker")**
09. Unlocking merge destination **branch**
10. Blaming on conflicting files
  - Only **BOTH_ADDED** and **BOTH_MODIFIED** conflicts will be blamed
11. Sending merge summary mail

Configurations:
---------------
All **optional** settings are not required, and can be commented out

```
# teamforge settings (optional)
ctf.server.url=https://your.teamforge.instance
ctf.username=john.doe
ctf.password=p4ssW0rd

# package id(s) for lock/unlock branches (optional)
master=pkg0001,pkg0002
dev=pkg0003

# git user settings (optional)
git.username=john.doe
git.password=p4ssW0rd
git.email=john.doe@example.com

# git repository directory (MUST be appended with $GIT_DIR, normally .git)
git.repo.dir=/path/to/git/worktree/.git

# git blame settings
git.blame.skip=false
git.blame.excludes=jar,par,tar,rar,zip,7z,rpt

# mail settings
mail.skip=false
mail.smtp.host=smtp.example.com
mail.smtp.port=25
mail.default.domain=example.com
mail.username=john.doe
mail.password=p4ssW0rd


# ----- you probably don't want to change the settings below -----
git.remote=origin
git.fetch.refspecs=+refs/heads/*:refs/remotes/origin/*,+refs/svn/map:refs/notes/commits
git.progress.monitor=false
```

License:
--------
[GNU GENERAL PUBLIC LICENSE v2.0](./LICENSE "See license")
