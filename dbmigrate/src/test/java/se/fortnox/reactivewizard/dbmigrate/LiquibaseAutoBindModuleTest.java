package se.fortnox.reactivewizard.dbmigrate;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;
import liquibase.exception.DatabaseException;
import liquibase.exception.LiquibaseException;
import org.junit.Assert;
import org.junit.Test;
import se.fortnox.reactivewizard.binding.AutoBindModules;
import se.fortnox.reactivewizard.config.ConfigFactory;
import se.fortnox.reactivewizard.config.TestInjector;
import se.fortnox.reactivewizard.json.JsonConfig;
import se.fortnox.reactivewizard.server.ServerConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

public class LiquibaseAutoBindModuleTest {
    @Test
    public void shouldOnlyRun() throws LiquibaseException {
        LiquibaseMigrate liquibaseMigrateMock = getInjectedLiquibaseMock("run", "config.yml");

        verify(liquibaseMigrateMock, never()).drop();
        verify(liquibaseMigrateMock, never()).run();
        verify(liquibaseMigrateMock, never()).exit();
    }

    @Test
    public void shouldDropMigrateAndExit() throws LiquibaseException {
        LiquibaseMigrate liquibaseMigrateMock = getInjectedLiquibaseMock("db-drop-migrate", "config.yml");

        verify(liquibaseMigrateMock).drop();
        verify(liquibaseMigrateMock).run();
        verify(liquibaseMigrateMock).exit();
    }

    @Test
    public void shouldNotExecuteLiquibase() throws LiquibaseException {
        LiquibaseMigrate liquibaseMigrateMock = getInjectedLiquibaseMock("db-run");

        verify(liquibaseMigrateMock, never()).drop();
        verify(liquibaseMigrateMock, never()).run();
        verify(liquibaseMigrateMock, never()).exit();
    }


    @Test
    public void shouldJustDrop() throws LiquibaseException {
        LiquibaseMigrate liquibaseMigrateMock = getInjectedLiquibaseMock("db-drop-run", "config.yml");

        verify(liquibaseMigrateMock).drop();
        verify(liquibaseMigrateMock, never()).run();
        verify(liquibaseMigrateMock, never()).exit();
    }

    @Test
    public void shouldJustRollback() throws LiquibaseException {
        LiquibaseMigrate liquibaseMigrateMock = getInjectedLiquibaseMock("db-rollback", "config.yml");

        verify(liquibaseMigrateMock, atLeastOnce()).rollback();
        verify(liquibaseMigrateMock, never()).run();
        verify(liquibaseMigrateMock, atLeastOnce()).exit();
    }

    @Test
    public void shouldContinueRunningAfterDropThrowsException() throws LiquibaseException {
        LiquibaseMigrate liquibaseMigrateMock = mock(LiquibaseMigrate.class);
        doThrow(new DatabaseException()).when(liquibaseMigrateMock).drop();

        getInjectedLiquibaseMock(liquibaseMigrateMock, "db-drop-migrate", "config.yml");

        verify(liquibaseMigrateMock).drop();
        verify(liquibaseMigrateMock).run();
        verify(liquibaseMigrateMock).exit();
    }

    @Test
    public void shouldAbortIfRunThrowsException() throws LiquibaseException {
        LiquibaseMigrate liquibaseMigrateMock = mock(LiquibaseMigrate.class);
        doThrow(new LiquibaseException()).when(liquibaseMigrateMock).run();

        try {
            getInjectedLiquibaseMock(liquibaseMigrateMock, "db-migrate", "config.yml");
            fail("Expected CreationException, but none was thrown");
        } catch (RuntimeException e) {
            assertThat(e.getCause()).isInstanceOf(LiquibaseException.class);
        }

        verify(liquibaseMigrateMock, never()).drop();
        verify(liquibaseMigrateMock).run();
        verify(liquibaseMigrateMock, never()).exit();
    }

    @Test
    public void shouldNotRunLiquibaseWhenOnlyConfigIsPassed() throws LiquibaseException {
        LiquibaseMigrate liquibaseMigrateMock = mock(LiquibaseMigrate.class);
        getInjectedLiquibaseMock(liquibaseMigrateMock, "config.yml");

        verify(liquibaseMigrateMock, never()).drop();
        verify(liquibaseMigrateMock, never()).run();
        verify(liquibaseMigrateMock, never()).exit();
    }

    @Test
    public void shouldBeAbleToRunLiquibaseMigrationsOnStartup() {

        try {
            TestInjector.create(binder -> {
            }, "test.bind.yml", new String[]{"db-migrate-run"});
        } catch (Exception e) {
            Assert.fail("Running liquibase migrations from file should not fail:" + e.getMessage());
        }

    }

    private LiquibaseMigrate getInjectedLiquibaseMock(String...arg) {
        LiquibaseMigrate liquibaseMigrateMock = mock(LiquibaseMigrate.class);

        return getInjectedLiquibaseMock(liquibaseMigrateMock, arg);
    }

    private LiquibaseMigrate getInjectedLiquibaseMock(LiquibaseMigrate liquibaseMigrateMock, String... arg) {
        LiquibaseMigrateProvider liquibaseMigrateProvider = mock(LiquibaseMigrateProvider.class);
        when(liquibaseMigrateProvider.get()).thenReturn(liquibaseMigrateMock);

        AbstractModule module = new AbstractModule(){
            @Override
            protected void configure() {
                ConfigFactory configFactory = mock(ConfigFactory.class);

                ServerConfig serverConfig = new ServerConfig() {{
                    setEnabled(false);
                }};
                when(configFactory.get(ServerConfig.class)).thenReturn(serverConfig);
                bind(ServerConfig.class).toInstance(serverConfig);

                JsonConfig jsonConfig = new JsonConfig();
                when(configFactory.get(JsonConfig.class)).thenReturn(jsonConfig);
                bind(JsonConfig.class).toInstance(jsonConfig);

                LiquibaseConfig liquibaseConfig = new LiquibaseConfig();
                liquibaseConfig.setUrl("jdbc:h2:mem:test");

                when(configFactory.get(LiquibaseConfig.class)).thenReturn(liquibaseConfig);
                bind(ConfigFactory.class).toInstance(configFactory);

                bind(String[].class)
                    .annotatedWith(Names.named("args"))
                    .toInstance(arg);

                bind(LiquibaseMigrateProvider.class).toInstance(liquibaseMigrateProvider);
            }
        };
        LiquibaseAutoBindModule liquibaseAutoBindModule = new LiquibaseAutoBindModule(arg, liquibaseMigrateProvider);
        liquibaseAutoBindModule.preBind();
        Guice.createInjector(new AutoBindModules(Modules.override(module).with(liquibaseAutoBindModule)));

        return liquibaseMigrateMock;
    }
}
