{
  description = "Editor Code Assistant (ECA) - AI pair programming capabilities agnostic of editor ";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
        jdk = pkgs.jdk24_headless;
        graalvm = pkgs.graalvmPackages.graalvm-ce;
        clojure = pkgs.clojure.override { jdk = pkgs.jdk_headless; };

      in
      {
        devShells.default = pkgs.mkShell {
          packages = with pkgs; [
            jdk
            clojure
            clojure-lsp
            babashka
            graalvm
            git
          ];

          env = {
            JAVA_HOME = "${jdk}";
            GRAALVM_HOME = "${graalvm}";

          };

        };

        packages.default = pkgs.stdenv.mkDerivation {
          pname = "eca";
          version = "0.1.0";
          src = ./.;

          nativeBuildInputs = with pkgs; [
            jdk
            clojure
            babashka
            graalvm
            git
          ];

          CLJ_CONFIG = "${placeholder "out"}/.clojure";
          CLJ_CACHE = "${placeholder "out"}/.cpcache";

          configurePhase = ''
            mkdir -p $CLJ_CONFIG $CLJ_CACHE
          '';

          buildPhase = ''
            bb native-cli
          '';

          installPhase = ''
            cp eca $out/bin/
          '';
        };
      }
    );
}
