package org.gradle.accessors.dm;

import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.plugin.use.PluginDependency;
import org.gradle.api.artifacts.ExternalModuleDependencyBundle;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.provider.Provider;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.internal.catalog.AbstractExternalDependencyFactory;
import org.gradle.api.internal.catalog.DefaultVersionCatalog;
import java.util.Map;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParser;
import javax.inject.Inject;

/**
 * A catalog of dependencies accessible via the `libs` extension.
 */
@NonNullApi
public class LibrariesForLibs extends AbstractExternalDependencyFactory {

    private final AbstractExternalDependencyFactory owner = this;
    private final FixtureLibraryAccessors laccForFixtureLibraryAccessors = new FixtureLibraryAccessors(owner);
    private final JacksonLibraryAccessors laccForJacksonLibraryAccessors = new JacksonLibraryAccessors(owner);
    private final MicrometerLibraryAccessors laccForMicrometerLibraryAccessors = new MicrometerLibraryAccessors(owner);
    private final MysqlLibraryAccessors laccForMysqlLibraryAccessors = new MysqlLibraryAccessors(owner);
    private final SpringLibraryAccessors laccForSpringLibraryAccessors = new SpringLibraryAccessors(owner);
    private final TestLibraryAccessors laccForTestLibraryAccessors = new TestLibraryAccessors(owner);
    private final VersionAccessors vaccForVersionAccessors = new VersionAccessors(providers, config);
    private final BundleAccessors baccForBundleAccessors = new BundleAccessors(objects, providers, config, attributesFactory, capabilityNotationParser);
    private final PluginAccessors paccForPluginAccessors = new PluginAccessors(providers, config);

    @Inject
    public LibrariesForLibs(DefaultVersionCatalog config, ProviderFactory providers, ObjectFactory objects, ImmutableAttributesFactory attributesFactory, CapabilityNotationParser capabilityNotationParser) {
        super(config, providers, objects, attributesFactory, capabilityNotationParser);
    }

        /**
         * Creates a dependency provider for assertj (org.assertj:assertj-core)
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getAssertj() {
            return create("assertj");
    }

        /**
         * Creates a dependency provider for h2 (com.h2database:h2)
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getH2() {
            return create("h2");
    }

    /**
     * Returns the group of libraries at fixture
     */
    public FixtureLibraryAccessors getFixture() {
        return laccForFixtureLibraryAccessors;
    }

    /**
     * Returns the group of libraries at jackson
     */
    public JacksonLibraryAccessors getJackson() {
        return laccForJacksonLibraryAccessors;
    }

    /**
     * Returns the group of libraries at micrometer
     */
    public MicrometerLibraryAccessors getMicrometer() {
        return laccForMicrometerLibraryAccessors;
    }

    /**
     * Returns the group of libraries at mysql
     */
    public MysqlLibraryAccessors getMysql() {
        return laccForMysqlLibraryAccessors;
    }

    /**
     * Returns the group of libraries at spring
     */
    public SpringLibraryAccessors getSpring() {
        return laccForSpringLibraryAccessors;
    }

    /**
     * Returns the group of libraries at test
     */
    public TestLibraryAccessors getTest() {
        return laccForTestLibraryAccessors;
    }

    /**
     * Returns the group of versions at versions
     */
    public VersionAccessors getVersions() {
        return vaccForVersionAccessors;
    }

    /**
     * Returns the group of bundles at bundles
     */
    public BundleAccessors getBundles() {
        return baccForBundleAccessors;
    }

    /**
     * Returns the group of plugins at plugins
     */
    public PluginAccessors getPlugins() {
        return paccForPluginAccessors;
    }

    public static class FixtureLibraryAccessors extends SubDependencyFactory {
        private final FixtureMonkeyLibraryAccessors laccForFixtureMonkeyLibraryAccessors = new FixtureMonkeyLibraryAccessors(owner);

        public FixtureLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Returns the group of libraries at fixture.monkey
         */
        public FixtureMonkeyLibraryAccessors getMonkey() {
            return laccForFixtureMonkeyLibraryAccessors;
        }

    }

    public static class FixtureMonkeyLibraryAccessors extends SubDependencyFactory {
        private final FixtureMonkeyStarterLibraryAccessors laccForFixtureMonkeyStarterLibraryAccessors = new FixtureMonkeyStarterLibraryAccessors(owner);

        public FixtureMonkeyLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Returns the group of libraries at fixture.monkey.starter
         */
        public FixtureMonkeyStarterLibraryAccessors getStarter() {
            return laccForFixtureMonkeyStarterLibraryAccessors;
        }

    }

    public static class FixtureMonkeyStarterLibraryAccessors extends SubDependencyFactory {

        public FixtureMonkeyStarterLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

            /**
             * Creates a dependency provider for kotlin (com.navercorp.fixturemonkey:fixture-monkey-starter-kotlin)
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getKotlin() {
                return create("fixture.monkey.starter.kotlin");
        }

    }

    public static class JacksonLibraryAccessors extends SubDependencyFactory {

        public JacksonLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

            /**
             * Creates a dependency provider for kotlin (com.fasterxml.jackson.module:jackson-module-kotlin)
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getKotlin() {
                return create("jackson.kotlin");
        }

    }

    public static class MicrometerLibraryAccessors extends SubDependencyFactory {
        private final MicrometerRegistryLibraryAccessors laccForMicrometerRegistryLibraryAccessors = new MicrometerRegistryLibraryAccessors(owner);
        private final MicrometerTracingLibraryAccessors laccForMicrometerTracingLibraryAccessors = new MicrometerTracingLibraryAccessors(owner);

        public MicrometerLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Returns the group of libraries at micrometer.registry
         */
        public MicrometerRegistryLibraryAccessors getRegistry() {
            return laccForMicrometerRegistryLibraryAccessors;
        }

        /**
         * Returns the group of libraries at micrometer.tracing
         */
        public MicrometerTracingLibraryAccessors getTracing() {
            return laccForMicrometerTracingLibraryAccessors;
        }

    }

    public static class MicrometerRegistryLibraryAccessors extends SubDependencyFactory {

        public MicrometerRegistryLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

            /**
             * Creates a dependency provider for prometheus (io.micrometer:micrometer-registry-prometheus)
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getPrometheus() {
                return create("micrometer.registry.prometheus");
        }

    }

    public static class MicrometerTracingLibraryAccessors extends SubDependencyFactory {
        private final MicrometerTracingBridgeLibraryAccessors laccForMicrometerTracingBridgeLibraryAccessors = new MicrometerTracingBridgeLibraryAccessors(owner);

        public MicrometerTracingLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Returns the group of libraries at micrometer.tracing.bridge
         */
        public MicrometerTracingBridgeLibraryAccessors getBridge() {
            return laccForMicrometerTracingBridgeLibraryAccessors;
        }

    }

    public static class MicrometerTracingBridgeLibraryAccessors extends SubDependencyFactory {

        public MicrometerTracingBridgeLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

            /**
             * Creates a dependency provider for brave (io.micrometer:micrometer-tracing-bridge-brave)
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getBrave() {
                return create("micrometer.tracing.bridge.brave");
        }

    }

    public static class MysqlLibraryAccessors extends SubDependencyFactory {

        public MysqlLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

            /**
             * Creates a dependency provider for connector (com.mysql:mysql-connector-j)
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getConnector() {
                return create("mysql.connector");
        }

    }

    public static class SpringLibraryAccessors extends SubDependencyFactory {
        private final SpringBootLibraryAccessors laccForSpringBootLibraryAccessors = new SpringBootLibraryAccessors(owner);
        private final SpringCloudLibraryAccessors laccForSpringCloudLibraryAccessors = new SpringCloudLibraryAccessors(owner);

        public SpringLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

            /**
             * Creates a dependency provider for context (org.springframework:spring-context)
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getContext() {
                return create("spring.context");
        }

            /**
             * Creates a dependency provider for mockk (com.ninja-squad:springmockk)
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getMockk() {
                return create("spring.mockk");
        }

        /**
         * Returns the group of libraries at spring.boot
         */
        public SpringBootLibraryAccessors getBoot() {
            return laccForSpringBootLibraryAccessors;
        }

        /**
         * Returns the group of libraries at spring.cloud
         */
        public SpringCloudLibraryAccessors getCloud() {
            return laccForSpringCloudLibraryAccessors;
        }

    }

    public static class SpringBootLibraryAccessors extends SubDependencyFactory {
        private final SpringBootConfigurationLibraryAccessors laccForSpringBootConfigurationLibraryAccessors = new SpringBootConfigurationLibraryAccessors(owner);
        private final SpringBootStarterLibraryAccessors laccForSpringBootStarterLibraryAccessors = new SpringBootStarterLibraryAccessors(owner);

        public SpringBootLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

            /**
             * Creates a dependency provider for dependencies (org.springframework.boot:spring-boot-dependencies)
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getDependencies() {
                return create("spring.boot.dependencies");
        }

            /**
             * Creates a dependency provider for testcontainers (org.springframework.boot:spring-boot-testcontainers)
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getTestcontainers() {
                return create("spring.boot.testcontainers");
        }

        /**
         * Returns the group of libraries at spring.boot.configuration
         */
        public SpringBootConfigurationLibraryAccessors getConfiguration() {
            return laccForSpringBootConfigurationLibraryAccessors;
        }

        /**
         * Returns the group of libraries at spring.boot.starter
         */
        public SpringBootStarterLibraryAccessors getStarter() {
            return laccForSpringBootStarterLibraryAccessors;
        }

    }

    public static class SpringBootConfigurationLibraryAccessors extends SubDependencyFactory {

        public SpringBootConfigurationLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

            /**
             * Creates a dependency provider for processor (org.springframework.boot:spring-boot-configuration-processor)
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getProcessor() {
                return create("spring.boot.configuration.processor");
        }

    }

    public static class SpringBootStarterLibraryAccessors extends SubDependencyFactory {
        private final SpringBootStarterDataLibraryAccessors laccForSpringBootStarterDataLibraryAccessors = new SpringBootStarterDataLibraryAccessors(owner);

        public SpringBootStarterLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

            /**
             * Creates a dependency provider for actuator (org.springframework.boot:spring-boot-starter-actuator)
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getActuator() {
                return create("spring.boot.starter.actuator");
        }

            /**
             * Creates a dependency provider for test (org.springframework.boot:spring-boot-starter-test)
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getTest() {
                return create("spring.boot.starter.test");
        }

            /**
             * Creates a dependency provider for web (org.springframework.boot:spring-boot-starter-web)
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getWeb() {
                return create("spring.boot.starter.web");
        }

        /**
         * Returns the group of libraries at spring.boot.starter.data
         */
        public SpringBootStarterDataLibraryAccessors getData() {
            return laccForSpringBootStarterDataLibraryAccessors;
        }

    }

    public static class SpringBootStarterDataLibraryAccessors extends SubDependencyFactory {

        public SpringBootStarterDataLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

            /**
             * Creates a dependency provider for jpa (org.springframework.boot:spring-boot-starter-data-jpa)
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getJpa() {
                return create("spring.boot.starter.data.jpa");
        }

    }

    public static class SpringCloudLibraryAccessors extends SubDependencyFactory {

        public SpringCloudLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

            /**
             * Creates a dependency provider for dependencies (org.springframework.cloud:spring-cloud-dependencies)
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getDependencies() {
                return create("spring.cloud.dependencies");
        }

    }

    public static class TestLibraryAccessors extends SubDependencyFactory {
        private final TestContainersLibraryAccessors laccForTestContainersLibraryAccessors = new TestContainersLibraryAccessors(owner);

        public TestLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Returns the group of libraries at test.containers
         */
        public TestContainersLibraryAccessors getContainers() {
            return laccForTestContainersLibraryAccessors;
        }

    }

    public static class TestContainersLibraryAccessors extends SubDependencyFactory {
        private final TestContainersJunitLibraryAccessors laccForTestContainersJunitLibraryAccessors = new TestContainersJunitLibraryAccessors(owner);

        public TestContainersLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

            /**
             * Creates a dependency provider for mysql (org.testcontainers:mysql)
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getMysql() {
                return create("test.containers.mysql");
        }

        /**
         * Returns the group of libraries at test.containers.junit
         */
        public TestContainersJunitLibraryAccessors getJunit() {
            return laccForTestContainersJunitLibraryAccessors;
        }

    }

    public static class TestContainersJunitLibraryAccessors extends SubDependencyFactory {

        public TestContainersJunitLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

            /**
             * Creates a dependency provider for jupiter (org.testcontainers:junit-jupiter)
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getJupiter() {
                return create("test.containers.junit.jupiter");
        }

    }

    public static class VersionAccessors extends VersionFactory  {

        private final FixtureVersionAccessors vaccForFixtureVersionAccessors = new FixtureVersionAccessors(providers, config);
        private final KtlintVersionAccessors vaccForKtlintVersionAccessors = new KtlintVersionAccessors(providers, config);
        private final SpringVersionAccessors vaccForSpringVersionAccessors = new SpringVersionAccessors(providers, config);
        private final TestVersionAccessors vaccForTestVersionAccessors = new TestVersionAccessors(providers, config);
        public VersionAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

            /**
             * Returns the version associated to this alias: assertj (3.24.2)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getAssertj() { return getVersion("assertj"); }

            /**
             * Returns the version associated to this alias: junit (5.9.3)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getJunit() { return getVersion("junit"); }

            /**
             * Returns the version associated to this alias: kotlin (1.9.21)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getKotlin() { return getVersion("kotlin"); }

            /**
             * Returns the version associated to this alias: redisson (3.25.2)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getRedisson() { return getVersion("redisson"); }

        /**
         * Returns the group of versions at versions.fixture
         */
        public FixtureVersionAccessors getFixture() {
            return vaccForFixtureVersionAccessors;
        }

        /**
         * Returns the group of versions at versions.ktlint
         */
        public KtlintVersionAccessors getKtlint() {
            return vaccForKtlintVersionAccessors;
        }

        /**
         * Returns the group of versions at versions.spring
         */
        public SpringVersionAccessors getSpring() {
            return vaccForSpringVersionAccessors;
        }

        /**
         * Returns the group of versions at versions.test
         */
        public TestVersionAccessors getTest() {
            return vaccForTestVersionAccessors;
        }

    }

    public static class FixtureVersionAccessors extends VersionFactory  {

        public FixtureVersionAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

            /**
             * Returns the version associated to this alias: fixture.monkey (1.0.13)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getMonkey() { return getVersion("fixture.monkey"); }

    }

    public static class KtlintVersionAccessors extends VersionFactory  {

        public KtlintVersionAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

            /**
             * Returns the version associated to this alias: ktlint.plugin (11.6.1)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getPlugin() { return getVersion("ktlint.plugin"); }

    }

    public static class SpringVersionAccessors extends VersionFactory  {

        private final SpringCloudVersionAccessors vaccForSpringCloudVersionAccessors = new SpringCloudVersionAccessors(providers, config);
        private final SpringIoVersionAccessors vaccForSpringIoVersionAccessors = new SpringIoVersionAccessors(providers, config);
        public SpringVersionAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

            /**
             * Returns the version associated to this alias: spring.boot (3.2.0)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getBoot() { return getVersion("spring.boot"); }

            /**
             * Returns the version associated to this alias: spring.mockk (4.0.2)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getMockk() { return getVersion("spring.mockk"); }

        /**
         * Returns the group of versions at versions.spring.cloud
         */
        public SpringCloudVersionAccessors getCloud() {
            return vaccForSpringCloudVersionAccessors;
        }

        /**
         * Returns the group of versions at versions.spring.io
         */
        public SpringIoVersionAccessors getIo() {
            return vaccForSpringIoVersionAccessors;
        }

    }

    public static class SpringCloudVersionAccessors extends VersionFactory  {

        public SpringCloudVersionAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

            /**
             * Returns the version associated to this alias: spring.cloud.dependencies (2023.0.0)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getDependencies() { return getVersion("spring.cloud.dependencies"); }

    }

    public static class SpringIoVersionAccessors extends VersionFactory  {

        private final SpringIoDependencyVersionAccessors vaccForSpringIoDependencyVersionAccessors = new SpringIoDependencyVersionAccessors(providers, config);
        public SpringIoVersionAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

        /**
         * Returns the group of versions at versions.spring.io.dependency
         */
        public SpringIoDependencyVersionAccessors getDependency() {
            return vaccForSpringIoDependencyVersionAccessors;
        }

    }

    public static class SpringIoDependencyVersionAccessors extends VersionFactory  {

        public SpringIoDependencyVersionAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

            /**
             * Returns the version associated to this alias: spring.io.dependency.management (1.1.0)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getManagement() { return getVersion("spring.io.dependency.management"); }

    }

    public static class TestVersionAccessors extends VersionFactory  {

        public TestVersionAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

            /**
             * Returns the version associated to this alias: test.containers (1.19.3)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getContainers() { return getVersion("test.containers"); }

    }

    public static class BundleAccessors extends BundleFactory {
        private final TestcontainersBundleAccessors baccForTestcontainersBundleAccessors = new TestcontainersBundleAccessors(objects, providers, config, attributesFactory, capabilityNotationParser);

        public BundleAccessors(ObjectFactory objects, ProviderFactory providers, DefaultVersionCatalog config, ImmutableAttributesFactory attributesFactory, CapabilityNotationParser capabilityNotationParser) { super(objects, providers, config, attributesFactory, capabilityNotationParser); }

        /**
         * Returns the group of bundles at bundles.testcontainers
         */
        public TestcontainersBundleAccessors getTestcontainers() {
            return baccForTestcontainersBundleAccessors;
        }

    }

    public static class TestcontainersBundleAccessors extends BundleFactory {

        public TestcontainersBundleAccessors(ObjectFactory objects, ProviderFactory providers, DefaultVersionCatalog config, ImmutableAttributesFactory attributesFactory, CapabilityNotationParser capabilityNotationParser) { super(objects, providers, config, attributesFactory, capabilityNotationParser); }

            /**
             * Creates a dependency bundle provider for testcontainers.mysql which is an aggregate for the following dependencies:
             * <ul>
             *    <li>org.testcontainers:mysql</li>
             *    <li>org.springframework.boot:spring-boot-testcontainers</li>
             *    <li>org.testcontainers:junit-jupiter</li>
             *    <li>org.springframework.boot:spring-boot-starter-test</li>
             * </ul>
             * This bundle was declared in catalog libs.versions.toml
             */
            public Provider<ExternalModuleDependencyBundle> getMysql() {
                return createBundle("testcontainers.mysql");
            }

    }

    public static class PluginAccessors extends PluginFactory {
        private final KotlinPluginAccessors paccForKotlinPluginAccessors = new KotlinPluginAccessors(providers, config);
        private final SpringPluginAccessors paccForSpringPluginAccessors = new SpringPluginAccessors(providers, config);

        public PluginAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

            /**
             * Creates a plugin provider for ktlint to the plugin id 'org.jlleitschuh.gradle.ktlint'
             * This plugin was declared in catalog libs.versions.toml
             */
            public Provider<PluginDependency> getKtlint() { return createPlugin("ktlint"); }

        /**
         * Returns the group of plugins at plugins.kotlin
         */
        public KotlinPluginAccessors getKotlin() {
            return paccForKotlinPluginAccessors;
        }

        /**
         * Returns the group of plugins at plugins.spring
         */
        public SpringPluginAccessors getSpring() {
            return paccForSpringPluginAccessors;
        }

    }

    public static class KotlinPluginAccessors extends PluginFactory {

        public KotlinPluginAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

            /**
             * Creates a plugin provider for kotlin.jpa to the plugin id 'org.jetbrains.kotlin.plugin.jpa'
             * This plugin was declared in catalog libs.versions.toml
             */
            public Provider<PluginDependency> getJpa() { return createPlugin("kotlin.jpa"); }

            /**
             * Creates a plugin provider for kotlin.jvm to the plugin id 'org.jetbrains.kotlin.jvm'
             * This plugin was declared in catalog libs.versions.toml
             */
            public Provider<PluginDependency> getJvm() { return createPlugin("kotlin.jvm"); }

            /**
             * Creates a plugin provider for kotlin.kapt to the plugin id 'org.jetbrains.kotlin.kapt'
             * This plugin was declared in catalog libs.versions.toml
             */
            public Provider<PluginDependency> getKapt() { return createPlugin("kotlin.kapt"); }

            /**
             * Creates a plugin provider for kotlin.spring to the plugin id 'org.jetbrains.kotlin.plugin.spring'
             * This plugin was declared in catalog libs.versions.toml
             */
            public Provider<PluginDependency> getSpring() { return createPlugin("kotlin.spring"); }

    }

    public static class SpringPluginAccessors extends PluginFactory {
        private final SpringDependencyPluginAccessors paccForSpringDependencyPluginAccessors = new SpringDependencyPluginAccessors(providers, config);

        public SpringPluginAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

            /**
             * Creates a plugin provider for spring.boot to the plugin id 'org.springframework.boot'
             * This plugin was declared in catalog libs.versions.toml
             */
            public Provider<PluginDependency> getBoot() { return createPlugin("spring.boot"); }

        /**
         * Returns the group of plugins at plugins.spring.dependency
         */
        public SpringDependencyPluginAccessors getDependency() {
            return paccForSpringDependencyPluginAccessors;
        }

    }

    public static class SpringDependencyPluginAccessors extends PluginFactory {

        public SpringDependencyPluginAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

            /**
             * Creates a plugin provider for spring.dependency.management to the plugin id 'io.spring.dependency-management'
             * This plugin was declared in catalog libs.versions.toml
             */
            public Provider<PluginDependency> getManagement() { return createPlugin("spring.dependency.management"); }

    }

}
