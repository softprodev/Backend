package io.raspberrywallet.manager;

import com.stasbar.Logger;
import io.raspberrywallet.contract.CommunicationChannel;
import io.raspberrywallet.manager.bitcoin.Bitcoin;
import io.raspberrywallet.manager.bitcoin.WalletCrypter;
import io.raspberrywallet.manager.cli.Opts;
import io.raspberrywallet.manager.database.Database;
import io.raspberrywallet.manager.linux.TemperatureMonitor;
import io.raspberrywallet.manager.modules.Module;
import io.raspberrywallet.manager.modules.ModuleClassLoader;
import io.raspberrywallet.server.KtorServer;
import org.apache.commons.cli.CommandLine;
import org.bitcoinj.store.BlockStoreException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static io.raspberrywallet.manager.cli.CliUtils.parseArgs;

public class Main {

    public static void main(String... args) throws BlockStoreException, IOException {
        CommandLine cmd = parseArgs(args);

        if (Opts.MOCK_MANAGER.isSet(cmd)) {
            KtorServer.Companion.startMocking();
            return;
        }
        // create backend->frontend communication channel
        CommunicationChannel communicationChannel = new CommunicationChannel();

        File yamlConfigFile = new File(Opts.CONFIG.getValue(cmd));
        Configuration configuration = Configuration.fromYamlFile(yamlConfigFile);
        Bitcoin bitcoin = new Bitcoin(configuration, new WalletCrypter(), communicationChannel);

        List<Module> modules = ModuleClassLoader.getModules(configuration);

        TemperatureMonitor temperatureMonitor = new TemperatureMonitor();

        Database db = new Database(configuration);

        Manager manager = new Manager(
                configuration,
                db,
                modules,
                bitcoin,
                temperatureMonitor,
                communicationChannel);

        KtorServer ktorServer = new KtorServer(
                manager,
                configuration.getServerConfig(),
                communicationChannel);

        prepareShutdownHook(bitcoin);
        ktorServer.startBlocking();
    }

    private static void prepareShutdownHook(Bitcoin bitcoin) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("Finishing...");
            try {
                Objects.requireNonNull(bitcoin.getPeerGroup()).stop();
            } catch (NullPointerException e) {
                // Ignore
            }
            // Forcibly terminate the JVM because Orchid likes to spew non-daemon threads everywhere.
            System.exit(0);
        }));
    }
}
