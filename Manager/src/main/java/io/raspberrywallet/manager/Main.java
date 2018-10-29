package io.raspberrywallet.manager;

import com.stasbar.Logger;
import io.raspberrywallet.WalletNotInitialized;
import io.raspberrywallet.manager.bitcoin.Bitcoin;
import io.raspberrywallet.manager.cli.Opts;
import io.raspberrywallet.manager.database.Database;
import io.raspberrywallet.manager.linux.TemperatureMonitor;
import io.raspberrywallet.manager.modules.Module;
import io.raspberrywallet.manager.modules.ModuleClassLoader;
import io.raspberrywallet.server.Server;
import org.apache.commons.cli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.raspberrywallet.ktor.KtorServerKt.startKtorServer;
import static io.raspberrywallet.manager.cli.CliUtils.parseArgs;

public class Main {

    public static void main(String... args) throws IOException {
        CommandLine cmd = parseArgs(args);

        Bitcoin bitcoin = new Bitcoin();

        File modulesDir = new File(Opts.MODULES.getValue(cmd));
        List<Module> modules = ModuleClassLoader.getModulesFrom(modulesDir);

        TemperatureMonitor temperatureMonitor = new TemperatureMonitor();

        Database db = new Database();

        Manager manager = new Manager(db, modules, bitcoin, temperatureMonitor);

        if (Opts.VERTX.isSet(cmd) || Opts.SERVER.getValue(cmd).equals(Opts.VERTX.name()))
            new Server(manager).start();
        else
            startKtorServer(manager);

        prepareShutdownHook(bitcoin, manager);
    }

    private static void prepareShutdownHook(Bitcoin bitcoin, Manager manager) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("Finishing...");
            try {
                if (manager.lockWallet()) Logger.info("Wallet Encrypted");
                else Logger.err("Failed Wallet Encryption");

                bitcoin.getKit().stopAsync();
                bitcoin.getKit().awaitTerminated(3, TimeUnit.SECONDS);
            } catch (NullPointerException e) {
                Logger.err("Failed Wallet Encryption");
                e.printStackTrace();
            } catch (WalletNotInitialized walletNotInitialized) {
                Logger.d("Wallet was not inited so there is nothing to encrypt");
                walletNotInitialized.printStackTrace();
            } catch (TimeoutException e) {
                Logger.err(e.getMessage());
            }
            // Forcibly terminate the JVM because Orchid likes to spew non-daemon threads everywhere.
            Runtime.getRuntime().exit(0);
        }));
    }
}
