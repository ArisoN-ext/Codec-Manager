{
  description = "Codec Manager — LDAC/A2DP codec quality manager for Android";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config = {
          android_sdk.accept_license = true;
          allowUnfree = true;
        };
      };

      android = pkgs.androidenv.composeAndroidPackages {
        platformVersions = [ "37" ];
        buildToolsVersions = [ "36.0.0" "37.0.0" ];
        includeEmulator = false;
        includeSystemImages = false;
        includeSources = false;
        includeNDK = false;
      };

      sdk = android.androidsdk;
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        packages = [
          pkgs.jdk21
          pkgs.gradle_9
          sdk
          pkgs.unzip
          pkgs.zip
          pkgs.curl
          pkgs.jq
        ];

        JAVA_HOME = "${pkgs.jdk21}/lib/openjdk";
        ANDROID_HOME = "${sdk}/libexec/android-sdk";
        ANDROID_SDK_ROOT = "${sdk}/libexec/android-sdk";

        shellHook = ''
          export GRADLE_USER_HOME="$PWD/.gradle-home"
          export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
          echo "Codec Manager dev shell"
          echo "  JDK:     $(java -version 2>&1 | head -n1)"
          echo "  Gradle:  $(gradle --version | grep -m1 Gradle)"
          echo "  SDK:     $ANDROID_HOME"
          echo
          echo "  ./scripts/build.sh debug"
          echo "  ./scripts/build.sh release"
        '';
      };
    };
}
