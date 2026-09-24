package com.deploy;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Invariants of deploy/kubernetes/base that schema validation (kubeconform) can't catch:
// they tie the manifests to how the service is configured (ports, probes, env vars, DB timeout)
class KubernetesManifestsTest {

    private static final Path BASE = Path.of("deploy/kubernetes/base");

    private static final int API_PORT = 8080;
    private static final int MANAGEMENT_PORT = 9090;
    // DB_CONNECTION_TIMEOUT_MS default: with the DB down, readiness takes this long to answer 503
    private static final int DB_CONNECTION_TIMEOUT_SECONDS = 5;

    private static final List<Map<String, Object>> resources = new ArrayList<>();

    @BeforeAll
    static void loadBase() throws IOException {
        Map<String, Object> kustomization = new Yaml().load(Files.readString(BASE.resolve("kustomization.yaml")));
        for (Object file : list(kustomization, "resources")) {
            for (Object document : new Yaml().loadAll(Files.readString(BASE.resolve((String) file)))) {
                if (document != null) {
                    resources.add(map(document));
                }
            }
        }
    }

    @Test
    void shouldExposeTheApiAndManagementPortsOfTheImage() {
        Map<String, Object> container = container();
        Map<String, Integer> ports = new LinkedHashMap<>();
        for (Object port : list(container, "ports")) {
            ports.put((String) map(port).get("name"), (Integer) map(port).get("containerPort"));
        }

        assertEquals(Map.of("http", API_PORT, "management", MANAGEMENT_PORT), ports);
    }

    @Test
    void shouldProbeActuatorOnTheManagementPort() {
        Map<String, Object> container = container();

        assertProbe(container, "startupProbe", "/actuator/health/liveness");
        assertProbe(container, "livenessProbe", "/actuator/health/liveness");
        assertProbe(container, "readinessProbe", "/actuator/health/readiness");
    }

    @Test
    void shouldGiveReadinessMoreTimeThanTheDatabaseConnectionTimeout() {
        Map<String, Object> readiness = map(container().get("readinessProbe"));

        assertTrue((Integer) readiness.get("timeoutSeconds") > DB_CONNECTION_TIMEOUT_SECONDS,
                "readiness timeout must exceed the DB connection timeout, or a DB outage looks like a hung pod");
    }

    @Test
    void shouldRouteApiTrafficOnlyToTheApiPort() {
        Map<String, Object> api = resource("Service", "monitor");
        Map<String, Object> management = resource("Service", "monitor-management");

        assertEquals(List.of("http"), targetPorts(api));
        assertEquals(List.of("management"), targetPorts(management));
    }

    @Test
    void shouldRunAsNonRootWithAReadOnlyFilesystem() {
        Map<String, Object> podSpec = podSpec();
        Map<String, Object> podSecurity = map(podSpec.get("securityContext"));
        Map<String, Object> containerSecurity = map(container().get("securityContext"));

        assertEquals(true, podSecurity.get("runAsNonRoot"));
        assertEquals(true, containerSecurity.get("readOnlyRootFilesystem"));
        assertEquals(false, containerSecurity.get("allowPrivilegeEscalation"));
        assertEquals(List.of("ALL"), list(map(containerSecurity.get("capabilities")), "drop"));
        assertEquals(false, podSpec.get("automountServiceAccountToken"));
    }

    @Test
    void shouldMountAWritableTmpForTomcatAndTheJvm() {
        Map<String, Object> podSpec = podSpec();
        String tmpVolume = list(container(), "volumeMounts").stream().map(KubernetesManifestsTest::map)
                .filter(mount -> "/tmp".equals(mount.get("mountPath")))
                .map(mount -> (String) mount.get("name"))
                .findFirst().orElseThrow(() -> new AssertionError("/tmp is not mounted"));

        assertTrue(list(podSpec, "volumes").stream().map(KubernetesManifestsTest::map)
                .anyMatch(volume -> tmpVolume.equals(volume.get("name")) && volume.containsKey("emptyDir")));
    }

    // The image runs as a named user; the kubelet can only enforce runAsNonRoot on a numeric one
    @Test
    void shouldRunTheImageAsANumericNonRootUser() throws IOException {
        Matcher user = Pattern.compile("(?m)^USER\\s+(\\S+)").matcher(Files.readString(Path.of("Dockerfile")));

        assertTrue(user.find(), "Dockerfile has no USER");
        assertTrue(user.group(1).matches("[1-9][0-9]*:[0-9]+"), "USER must be numeric uid:gid, was " + user.group(1));
    }

    @Test
    void shouldBoundMemoryAndReserveResources() {
        Map<String, Object> resources = map(container().get("resources"));

        assertNotNull(map(resources.get("limits")).get("memory"));
        assertNotNull(map(resources.get("requests")).get("memory"));
        assertNotNull(map(resources.get("requests")).get("cpu"));
    }

    // Kubernetes would otherwise inject MONITOR_*/POSTGRES_* variables for every Service in the namespace
    @Test
    void shouldNotInjectServiceLinkEnvironmentVariables() {
        assertEquals(false, podSpec().get("enableServiceLinks"));
    }

    @Test
    void shouldTakeDatabaseCredentialsFromASecret() {
        Map<String, Map<String, Object>> env = new LinkedHashMap<>();
        for (Object variable : list(container(), "env")) {
            env.put((String) map(variable).get("name"), map(variable));
        }

        for (String name : List.of("DB_USERNAME", "DB_PASSWORD")) {
            Map<String, Object> valueFrom = map(env.get(name).get("valueFrom"));
            assertTrue(valueFrom.containsKey("secretKeyRef"), name + " must come from a Secret");
        }
    }

    // A typo in the ConfigMap would silently fall back to the default
    @Test
    void shouldOnlyConfigureVariablesTheServiceReads() throws IOException {
        String applicationYml = Files.readString(Path.of("src/main/resources/application.yml"));
        List<String> keys = configEnvKeys();

        assertFalse(keys.isEmpty());
        for (String key : keys) {
            assertTrue(applicationYml.contains("${" + key + ":") || key.equals("LOGGING_STRUCTURED_FORMAT_CONSOLE"),
                    key + " is not read by application.yml");
            assertFalse(key.contains("PASSWORD") || key.contains("USERNAME"), key + " belongs in a Secret");
        }
    }

    @Test
    void shouldKeepAnInstanceAvailableDuringVoluntaryDisruptions() {
        Map<String, Object> deploymentSpec = map(resource("Deployment", "monitor").get("spec"));
        Map<String, Object> strategy = map(map(deploymentSpec.get("strategy")).get("rollingUpdate"));
        Map<String, Object> pdb = map(resource("PodDisruptionBudget", "monitor").get("spec"));

        assertTrue((Integer) deploymentSpec.get("replicas") >= 2);
        assertEquals(0, strategy.get("maxUnavailable"));
        assertEquals(1, pdb.get("minAvailable"));
    }

    private static void assertProbe(Map<String, Object> container, String probe, String path) {
        Map<String, Object> httpGet = map(map(container.get(probe)).get("httpGet"));
        assertEquals("management", httpGet.get("port"), probe);
        assertEquals(path, httpGet.get("path"), probe);
    }

    private static List<String> configEnvKeys() throws IOException {
        Map<String, Object> kustomization = new Yaml().load(Files.readString(BASE.resolve("kustomization.yaml")));
        List<String> keys = new ArrayList<>();
        for (Object generator : list(kustomization, "configMapGenerator")) {
            for (Object envFile : list(map(generator), "envs")) {
                try (Stream<String> lines = Files.lines(BASE.resolve((String) envFile))) {
                    lines.map(String::strip)
                            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                            .forEach(line -> keys.add(line.substring(0, line.indexOf('='))));
                }
            }
        }
        return keys;
    }

    private static List<String> targetPorts(Map<String, Object> service) {
        return list(map(service.get("spec")), "ports").stream().map(port -> (String) map(port).get("targetPort")).toList();
    }

    private static Map<String, Object> container() {
        List<Object> containers = list(podSpec(), "containers");
        assertEquals(1, containers.size());
        return map(containers.getFirst());
    }

    private static Map<String, Object> podSpec() {
        Map<String, Object> spec = map(resource("Deployment", "monitor").get("spec"));
        return map(map(spec.get("template")).get("spec"));
    }

    private static Map<String, Object> resource(String kind, String name) {
        return resources.stream()
                .filter(resource -> kind.equals(resource.get("kind")) && name.equals(map(resource.get("metadata")).get("name")))
                .findFirst().orElseThrow(() -> new AssertionError(kind + " " + name + " not found in " + BASE));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        assertNotNull(value);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        assertNotNull(value, key);
        return (List<Object>) value;
    }
}
