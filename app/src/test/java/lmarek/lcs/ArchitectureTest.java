package lmarek.lcs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class ArchitectureTest {
  private static final String ROOT_PACKAGE_NAME = "lmarek.lcs";

  @TestFactory
  Stream<DynamicTest> allPackagesMustBeNullMarked() {
    var rootPackage =
        new ClassFileImporter().importPackages(ROOT_PACKAGE_NAME).getPackage(ROOT_PACKAGE_NAME);
    return Stream.concat(Stream.of(rootPackage), rootPackage.getSubpackagesInTree().stream())
        .filter(pkg -> !pkg.getClasses().isEmpty())
        .map(
            pkg ->
                dynamicTest(
                    "package %s is @NullMarked".formatted(pkg.getName()),
                    () -> assertThat(pkg.isAnnotatedWith(NullMarked.class)).isTrue()));
  }
}
