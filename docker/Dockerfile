FROM ubuntu:24.04

ARG ANDROID_CMDLINE_TOOLS_BUILD=13114758
ARG RUST_TOOLCHAIN=1.97.1
ARG CARGO_NDK_VERSION=4.1.2

ENV DEBIAN_FRONTEND=noninteractive \
    ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        build-essential ca-certificates curl gh git jq openjdk-17-jdk-headless \
        ripgrep socat sudo tmux unzip \
    && rm -rf /var/lib/apt/lists/* \
    && echo "ubuntu ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/ubuntu

RUN mkdir -p "$ANDROID_HOME/cmdline-tools" \
    && curl -fsSL -o /tmp/cmdline-tools.zip \
        "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_BUILD}_latest.zip" \
    && unzip -q /tmp/cmdline-tools.zip -d "$ANDROID_HOME/cmdline-tools" \
    && mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest" \
    && rm /tmp/cmdline-tools.zip \
    && yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null \
    && "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
        "platform-tools" "platforms;android-37.0" "build-tools;36.0.0" "ndk;30.0.14904198" \
    && chown -R ubuntu:ubuntu "$ANDROID_HOME"

RUN mkdir -p /workspace /home/ubuntu/.claude /home/ubuntu/.gradle \
    && chown ubuntu:ubuntu /workspace /home/ubuntu/.claude /home/ubuntu/.gradle

COPY --chmod=755 entrypoint.sh /usr/local/bin/entrypoint.sh

USER ubuntu
ENV PATH=/home/ubuntu/.local/bin:/home/ubuntu/.cargo/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

RUN curl -fsSL https://sh.rustup.rs | sh -s -- -y --profile minimal --default-toolchain "$RUST_TOOLCHAIN" \
    && cargo install --locked cargo-ndk --version "$CARGO_NDK_VERSION"

RUN curl -fsSL https://claude.ai/install.sh | bash

WORKDIR /workspace
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
