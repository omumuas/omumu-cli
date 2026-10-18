#!/usr/bin/env bash
set -euo pipefail

# Usage: ./release.sh 0.3.0

VERSION="${1:?Usage: ./release.sh <version>}"
REPO_DIR="$(cd "$(dirname "$0")" && pwd)"
TAP_DIR="$REPO_DIR/../homebrew-omumu"

echo "=== Releasing omumu-cli v${VERSION} ==="

# 1. Update version in all files
echo "Updating version strings..."
sed -i '' "s|<version>.*</version><!-- cli-version -->|<version>${VERSION}</version><!-- cli-version -->|" "$REPO_DIR/pom.xml"
sed -i '' "s|version = \"omumu .*\"|version = \"omumu ${VERSION}\"|" "$REPO_DIR/src/main/java/com/omumu/cli/OmumuCli.java"

# Update all hardcoded version strings in Java source
find "$REPO_DIR/src" -name "*.java" -exec grep -l '"[0-9]\+\.[0-9]\+\.[0-9]\+"' {} \; | while read -r file; do
    sed -i '' "s|\"[0-9]\{1,\}\.[0-9]\{1,\}\.[0-9]\{1,\}\"|\"${VERSION}\"|g" "$file"
done

# Update README
sed -i '' "s|omumu-cli-[0-9]\{1,\}\.[0-9]\{1,\}\.[0-9]\{1,\}\.jar|omumu-cli-${VERSION}.jar|g" "$REPO_DIR/README.md"

echo "Version updated to ${VERSION}"

# 2. Build native binary
echo "Building native binary..."
cd "$REPO_DIR"
mvn clean package -Pnative -q
./target/omumu --version

# 3. Package
echo "Packaging..."
cd "$REPO_DIR/target"
tar -czf "omumu-${VERSION}-darwin-arm64.tar.gz" omumu
SHA256=$(shasum -a 256 "omumu-${VERSION}-darwin-arm64.tar.gz" | cut -d' ' -f1)
echo "SHA256: ${SHA256}"

# 4. Commit and tag
echo "Committing..."
cd "$REPO_DIR"
git add -A
git commit -m "Release v${VERSION}"
git tag "v${VERSION}"
git push && git push origin "v${VERSION}"

# 5. Prepare uber JAR for Intel/Linux fallback
cp "target/omumu-cli-${VERSION}.jar" "target/omumu-${VERSION}-uber.jar"

# 6. GitHub release
echo "Creating GitHub release..."
gh release create "v${VERSION}" \
  "target/omumu-${VERSION}-darwin-arm64.tar.gz" \
  "target/omumu-${VERSION}-uber.jar" \
  --title "v${VERSION}" \
  --generate-notes

# 7. Update Homebrew tap
if [ -d "$TAP_DIR" ]; then
    echo "Updating Homebrew tap..."
    cd "$TAP_DIR"
    cat > Formula/omumu.rb << 'FORMULA_EOF'
class Omumu < Formula
  desc "CLI for the Omumu customer education platform"
  homepage "https://github.com/omumuas/omumu-cli"
  version "VERSION_PLACEHOLDER"
  license "MIT"

  on_macos do
    on_arm do
      url "https://github.com/omumuas/omumu-cli/releases/download/vVERSION_PLACEHOLDER/omumu-VERSION_PLACEHOLDER-darwin-arm64.tar.gz"
      sha256 "SHA256_PLACEHOLDER"
    end

    on_intel do
      url "https://github.com/omumuas/omumu-cli/releases/download/vVERSION_PLACEHOLDER/omumu-VERSION_PLACEHOLDER-uber.jar"
      sha256 :no_check
      depends_on "openjdk@21"
    end
  end

  on_linux do
    url "https://github.com/omumuas/omumu-cli/releases/download/vVERSION_PLACEHOLDER/omumu-VERSION_PLACEHOLDER-uber.jar"
    sha256 :no_check
    depends_on "openjdk@21"
  end

  def install
    if File.exist?("omumu")
      bin.install "omumu"
    else
      libexec.install Dir["*.jar"].first => "omumu-cli.jar"
      (bin/"omumu").write <<~EOS
        #!/bin/bash
        exec java -jar "#{libexec}/omumu-cli.jar" "$@"
      EOS
    end
  end

  def caveats
    return if !OS.mac? || !Hardware::CPU.arm?

    <<~EOS
      Homebrew runs its Xcode checks for this formula even though nothing is
      compiled. On Apple Silicon the cask avoids that entirely:
        brew uninstall omumu
        brew install --cask omumuas/omumu/omumu
    EOS
  end

  test do
    assert_match "omumu", shell_output("#{bin}/omumu --version")
  end
end
FORMULA_EOF
    sed -i '' "s/VERSION_PLACEHOLDER/${VERSION}/g" Formula/omumu.rb
    sed -i '' "s/SHA256_PLACEHOLDER/${SHA256}/g" Formula/omumu.rb

    # The cask is the primary Apple Silicon install path. A non-bottle formula
    # makes Homebrew run its fatal Xcode version checks even though nothing is
    # compiled; casks skip those checks.
    mkdir -p Casks
    cat > Casks/omumu.rb << 'CASK_EOF'
cask "omumu" do
  version "VERSION_PLACEHOLDER"
  sha256 "SHA256_PLACEHOLDER"

  url "https://github.com/omumuas/omumu-cli/releases/download/v#{version}/omumu-#{version}-darwin-arm64.tar.gz"
  name "Omumu CLI"
  desc "CLI for the Omumu customer education platform"
  homepage "https://github.com/omumuas/omumu-cli"

  livecheck do
    url :url
    strategy :github_latest
  end

  # The native binary is only built for Apple Silicon. Intel Macs and Linux
  # use the formula, which runs the uber JAR on OpenJDK.
  depends_on arch: :arm64

  binary "omumu"

  # Homebrew quarantines cask downloads and the binary is not notarized, so
  # macOS would kill it on launch (exit 137) unless the attribute is cleared.
  # Homebrew 7 deprecates the `postflight` block in favour of `postflight_steps`;
  # Homebrew 6 only has the block, so support both.
  if respond_to?(:postflight_steps)
    postflight_steps do
      run "/usr/bin/xattr",
          args:         ["-d", "com.apple.quarantine", "{{staged_path}}/omumu"],
          must_succeed: false
    end
  else
    postflight do
      system_command "/usr/bin/xattr",
                     args:         ["-d", "com.apple.quarantine", "#{staged_path}/omumu"],
                     must_succeed: false
    end
  end

  caveats <<~EOS
    This cask installs the prebuilt Apple Silicon binary and never needs Xcode.
    If you installed the formula earlier, remove it first:
      brew uninstall omumu
  EOS
end
CASK_EOF
    sed -i '' "s/VERSION_PLACEHOLDER/${VERSION}/g" Casks/omumu.rb
    sed -i '' "s/SHA256_PLACEHOLDER/${SHA256}/g" Casks/omumu.rb
    git add -A
    git commit -m "Bump to v${VERSION}"
    git push
    echo "Homebrew tap updated."
else
    echo "WARNING: Homebrew tap not found at ${TAP_DIR}"
    echo "Update manually with SHA256: ${SHA256}"
fi

echo ""
echo "=== v${VERSION} released ==="
echo "Users can now: brew upgrade omumu (formula) or brew upgrade --cask omumu (Apple Silicon cask)"
