package me.itzg.helpers.modrinth;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static me.itzg.helpers.modrinth.ModrinthTestHelpers.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.modrinth.model.ModpackIndex;
import me.itzg.helpers.modrinth.model.ModpackIndex.ModpackFile;
import me.itzg.helpers.modrinth.model.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@WireMockTest
public class InstallModrinthModpackCommandTest {
    private final String projectName = "test_project1";
    private final String projectId = "efgh5678";
    private final String projectVersionId = "abcd1234";
    private final Version projectVersion =
        createModrinthProjectVersion(projectVersionId);

    static InstallModrinthModpackCommand createInstallModrinthModpackCommand(
        String baseUrl, Path outputDir, String projectName, String versionId,
        ModpackLoader loader)
    {
        InstallModrinthModpackCommand commandUT =
            new InstallModrinthModpackCommand();
        commandUT.baseUrl = baseUrl;
        commandUT.outputDirectory = outputDir;
        commandUT.modpackProject = projectName;
        commandUT.version = versionId;
        commandUT.loader = loader;

        return commandUT;
    }

    @Test
    void downloadsAndInstallsModrinthModpack(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws IOException, URISyntaxException
    {
        String expectedFileData = "some test data";
        String relativeFilePath = "test_file";
        ModpackFile testFile = createHostedModpackFile(
            relativeFilePath, expectedFileData, wm.getHttpBaseUrl());

        ModpackIndex index = createBasicModpackIndex();
        index.getFiles().add(testFile);

        stubModrinthModpackApi(
            wm, projectName, projectId, projectVersion,
            createModrinthPack(index));

        InstallModrinthModpackCommand commandUT =
            createInstallModrinthModpackCommand(wm.getHttpBaseUrl(), tempDir,
                projectName, projectVersionId, ModpackLoader.forge);

        int commandStatus = commandUT.call();

        assertThat(commandStatus).isEqualTo(0);
        assertThat(tempDir.resolve(relativeFilePath)).content()
            .isEqualTo(expectedFileData);
    }

    @Test
    void downloadsAndInstallsModrinthModpack_versionNumberAndAnyLoader(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws IOException, URISyntaxException
    {
        String expectedFileData = "some test data";
        String relativeFilePath = "test_file";
        ModpackFile testFile = createHostedModpackFile(
            relativeFilePath, expectedFileData, wm.getHttpBaseUrl());

        ModpackIndex index = createBasicModpackIndex();
        index.getFiles().add(testFile);

        String projectVersionNumber = "1.6.1";
        stubModrinthModpackApi(
            wm, projectName, this.projectId,
            createModrinthProjectVersion(projectVersionId).setVersionNumber(projectVersionNumber),
            createModrinthPack(index)
        );

        InstallModrinthModpackCommand commandUT =
            createInstallModrinthModpackCommand(wm.getHttpBaseUrl(), tempDir,
                projectName, projectVersionNumber, null);

        int commandStatus = commandUT.call();

        assertThat(commandStatus).isEqualTo(0);
        assertThat(tempDir.resolve(relativeFilePath)).content()
            .isEqualTo(expectedFileData);
    }

    @Test
    void createsModrinthModpackManifestForModpackInstallation(
                WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws IOException {
        ModpackIndex index = createBasicModpackIndex();

        stubModrinthModpackApi(
            wm, projectName, projectId, projectVersion,
            createModrinthPack(index));

        InstallModrinthModpackCommand commandUT =
            createInstallModrinthModpackCommand(wm.getHttpBaseUrl(), tempDir,
                projectName, projectVersionId, ModpackLoader.forge);

        int commandStatus = commandUT.call();

        assertThat(commandStatus).isEqualTo(0);

        ModrinthModpackManifest installedManifest = Manifests.load(tempDir,
            ModrinthModpackManifest.ID, ModrinthModpackManifest.class);

        assertThat(installedManifest).isNotNull();
        assertThat(installedManifest.getProjectSlug())
            .isEqualTo(projectName);
        assertThat(installedManifest.getVersionId())
            .isEqualTo(projectVersionId);
        assertThat(installedManifest.getFiles().size())
            .isEqualTo(0);
        assertThat(installedManifest.getDependencies())
            .isEqualTo(index.getDependencies());
    }

    @Test
    void removesFilesNoLongerNeedeByUpdatedModpack(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws IOException, URISyntaxException
    {
        String expectedFileData = "some test data";
        String relativeFilePath = "test_file";
        ModpackFile testFile = createHostedModpackFile(
            relativeFilePath, expectedFileData, wm.getHttpBaseUrl());

        ModpackIndex index = createBasicModpackIndex();
        index.getFiles().add(testFile);

        stubModrinthModpackApi(
            wm, projectName, projectId, projectVersion,
            createModrinthPack(index));

        createInstallModrinthModpackCommand(wm.getHttpBaseUrl(), tempDir,
            projectName, projectVersionId, ModpackLoader.forge)
            .call();

        String newProjectVersionId = "1234abcd";
        Version newProjectVersion =
            createModrinthProjectVersion(newProjectVersionId);
        index.getFiles().remove(testFile);

        stubModrinthModpackApi(
            wm, projectName, projectId, newProjectVersion,
            createModrinthPack(index));

        int commandStatus =
            createInstallModrinthModpackCommand(wm.getHttpBaseUrl(), tempDir,
                projectName, newProjectVersionId, ModpackLoader.forge)
                .call();

        assertThat(commandStatus).isEqualTo(0);
        assertThat(tempDir.resolve(relativeFilePath)).doesNotExist();
    }

    @Test
    void downloadsAndInstallsGenericModpacksOverHttp(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws IOException, URISyntaxException
    {
        String expectedFileData = "some test data";
        String relativeFilePath = "test_file";
        String modpackDownloadPath = "/files/modpacks/test_modpack-1.0.0.mrpack";
        ModpackFile testFile = createHostedModpackFile(
            relativeFilePath, expectedFileData, wm.getHttpBaseUrl());

        ModpackIndex index = createBasicModpackIndex();
        index.getFiles().add(testFile);

        stubFor(get(modpackDownloadPath)
            .willReturn(ok()
            .withHeader("Content-Type", "application/x-modrinth-modpack+zip")
            .withBody(createModrinthPack(index))));

        InstallModrinthModpackCommand commandUT =
            createInstallModrinthModpackCommand(wm.getHttpBaseUrl(), tempDir,
                wm.getHttpBaseUrl() + modpackDownloadPath, null, null);

        int commandStatus = commandUT.call();

        assertThat(commandStatus).isEqualTo(0);
        assertThat(tempDir.resolve(relativeFilePath)).content()
            .isEqualTo(expectedFileData);
    }
}