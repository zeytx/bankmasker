# Contributing to BankMasker

Thank you for considering contributing to BankMasker! 🎉

## Getting Started

1. **Fork** the repository
2. **Clone** your fork:
   ```bash
   git clone https://github.com/<your-user>/bankmasker.git
   cd bankmasker
   ```
3. **Build** the project:
   ```bash
   mvn clean verify
   ```

## Development Setup

- **Java 17+** is required
- **Maven 3.8+** for building
- IDE: IntelliJ IDEA recommended (import as Maven project)

## Branch Naming

| Type | Pattern | Example |
|------|---------|---------|
| Feature | `feature/<short-desc>` | `feature/add-passport-mask` |
| Bug fix | `fix/<short-desc>` | `fix/default-mask-char` |
| Docs | `docs/<short-desc>` | `docs/update-readme` |

## Making Changes

1. Create a branch from `main`
2. Write your code following the existing style
3. Add **tests** for any new functionality
4. Make sure all tests pass: `mvn clean verify`
5. Update `CHANGELOG.md` under `[Unreleased]`

## Code Style

- Follow existing conventions in the codebase
- All public classes and methods must have **Javadoc**
- Use `@since` tags for new public API
- Keep methods small and focused
- Prefer immutability

## Testing

- Unit tests go in `bankmasker-core/src/test/java/`
- Use JUnit 5 with `@Nested` classes for grouping
- Use `@DisplayName` for readable test names
- Use `@ParameterizedTest` with `@CsvSource` for data-driven tests
- Reset `MaskingConfig` in `@BeforeEach` / `@AfterEach`

## Pull Request Process

1. Ensure CI passes (GitHub Actions)
2. Update documentation if needed
3. Describe your changes clearly in the PR description
4. Link any related issues

## Adding a New Mask Type

1. Add the enum constant in `MaskType.java` with its strategy lambda
2. Add Javadoc with `@since` tag
3. Add tests in `MaskTypeTest.java` (parametrized)
4. Add tests in `MaskUtilsTest.java`
5. Add a DTO + nested test class in `MaskingSerializerTest.java`
6. Update the mask types table in `README.md`
7. Add the benchmark to `MaskingSerializerBenchmark.java` if relevant

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).

