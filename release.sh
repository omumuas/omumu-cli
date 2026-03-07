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

  test do
    assert_match "omumu", shell_output("#{bin}/omumu --version")
  end
end
FORMULA_EOF
    sed -i '' "s/VERSION_PLACEHOLDER/${VERSION}/g" Formula/omumu.rb
    sed -i '' "s/SHA256_PLACEHOLDER/${SHA256}/g" Formula/omumu.rb
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
echo "Users can now: brew upgrade omumu"
