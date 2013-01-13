@ECHO OFF
call env.bat

set PR_INSTALL=%~dp0prunsrv.exe

REM Service log configuration
set PR_LOGPREFIX=%SERVICE_NAME%
set PR_LOGPATH=%~dp0log
set PR_STDOUTPUT=%~dp0log\service_stdout.txt
set PR_STDERROR=%~dp0log\service_stderr.txt
set PR_LOGLEVEL=Error

REM Path to java installation
set PR_JVM=%~dp0..\jre\bin\server\jvm.dll
set PR_CLASSPATH=%~dp0..\ct.jar;%~dp0..

REM Startup configuration
set PR_STARTUP=auto
set PR_STARTMODE=jvm
set PR_STARTCLASS=ch.rasc.maven.plugin.execwar.run.Runner
set PR_STARTMETHOD=start

REM Shutdown configuration
set PR_STOPMODE=jvm
set PR_STOPCLASS=ch.rasc.maven.plugin.execwar.run.Runner
set PR_STOPMETHOD=stop

REM JVM configuration
set PR_JVMMS=256
set PR_JVMMX=1024
REM set PR_JVMSS=4000
set PR_JVMOPTIONS=-Xverify:none;-XX:MaxPermSize=160m

prunsrv.exe //IS//%SERVICE_NAME%