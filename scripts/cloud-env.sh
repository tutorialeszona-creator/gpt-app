# Source this file from the repository root in the managed Linux cloud workspace.
export MI_DEPARTAMENTO_TOOLS="${MI_DEPARTAMENTO_TOOLS:-/workspace/toolchains}"
export JAVA_HOME="$MI_DEPARTAMENTO_TOOLS/jdk-21.0.13+9"
export ANDROID_HOME="$MI_DEPARTAMENTO_TOOLS/android-sdk"
export GRADLE_USER_HOME="$MI_DEPARTAMENTO_TOOLS/gradle-user"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
