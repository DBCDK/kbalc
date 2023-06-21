{
  description = "kbalc, to balance your kafka logdirs";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

    flake-parts.url = "github:hercules-ci/flake-parts";

    treefmt-nix.url = "github:numtide/treefmt-nix";
    treefmt-nix.inputs.nixpkgs.follows = "nixpkgs";

    clj-nix.url = "github:jlesquembre/clj-nix";
    clj-nix.inputs.nixpkgs.follows = "nixpkgs";
  };

  outputs = inputs @ {
    self,
    flake-parts,
    ...
  }:
    flake-parts.lib.mkFlake {inherit inputs;} {
      systems = ["x86_64-linux"];

      imports = with inputs; [
        treefmt-nix.flakeModule
      ];

      perSystem = {
        self',
        inputs',
        pkgs,
        ...
      }: let
        package = "kbalc";
        cljpkgs = inputs'.clj-nix.packages;
      in {
        packages = {
          default = self'.packages."${package}-clj";

          "${package}-clj" = cljpkgs.mkCljBin {
            projectSrc = ./.;
            name = package;
            main-ns = "kbalc.core";
          };

          "${package}-graal" = cljpkgs.mkGraalBin {
            cljDrv = self'.packages."${package}-clj";
            extraNativeImageBuildArgs = [
              "-H:ReflectionConfigurationFiles=${./reflect-config.json}"
            ];
          };
        };

        devShells.default = pkgs.mkShell {
          inputsFrom = [
            self'.packages."${package}-clj"
          ];

          buildInputs = with pkgs; [
            clojure-lsp
            alejandra
            cljpkgs.deps-lock
          ];
        };

        treefmt = {
          projectRootFile = "flake.nix";
          programs.alejandra.enable = true;
        };
      };
    };
}
