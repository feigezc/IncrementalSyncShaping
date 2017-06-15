package com.alibaba.middleware.race.sync;


import com.alibaba.middleware.race.sync.network.NativeSocket.NativeServer;
import com.alibaba.middleware.race.sync.server.ServerPipelinedComputation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import static com.alibaba.middleware.race.sync.server.FileUtil.copyFiles;
import static com.alibaba.middleware.race.sync.server.FileUtil.transferFile;
import static com.alibaba.middleware.race.sync.server.ServerPipelinedComputation.JoinComputationThread;
import static com.alibaba.middleware.race.sync.server.ServerPipelinedComputation.OneRoundComputation;

/**
 * Created by will on 6/6/2017.
 */
public class Server {
    public static Logger logger;
    private static ArrayList<String> dataFiles = new ArrayList<>();
    private static NativeServer nativeServer = null;

    static {
        for (int i = 1; i < 11; i++) {
            dataFiles.add(i + ".txt");
        }
    }

    /**
     * 初始化系统属性
     */
    public static void initProperties() {
        System.setProperty("middleware.test.home", Constants.TESTER_HOME);
        System.setProperty("middleware.teamcode", Constants.TEAMCODE);
        System.setProperty("app.logging.level", Constants.LOG_LEVEL);
        System.setProperty("test.role", Constants.TEST_ROLE[0]);
    }

    private void printArgs(String[] args) {
        logger.info(args[0]);
        logger.info(args[1]);
        logger.info(args[2]);
        logger.info(args[3]);
    }

    public Server(String[] args) {
        logger.info("Current server time:" + System.currentTimeMillis());
        printArgs(args);
        logger.info(Constants.CODE_VERSION);
    }

    public static void main(String[] args) {

        Server.initProperties();
        logger = LoggerFactory.getLogger(Server.class);
        logger.info("Current server time:" + System.currentTimeMillis());

        nativeServer = new NativeServer(args, Constants.SERVER_PORT);
        nativeServer.start();

        long copyStartTimer = System.currentTimeMillis();

        for (int i = 1; i < 11; i++) {
            try {
                transferFile(i + ".txt", Constants.DATA_HOME, Constants.MIDDLE_HOME);
            } catch (IOException e) {
                logger.info(e.getMessage());
            }
        }

        long copyEndTimer = System.currentTimeMillis();
        logger.info(copyEndTimer - copyStartTimer + "");
        System.out.println(copyEndTimer - copyStartTimer + "");

        // initialization for computations
        //ServerPipelinedComputation.initSchemaTable(args[0], args[1]);
        ServerPipelinedComputation.initRange(Long.parseLong(args[2]), Long.parseLong(args[3]));
        ServerPipelinedComputation.initFindResultListener(new ServerPipelinedComputation.FindResultListener() {

            @Override
            public void sendToClient(String result) {
                //logger.info("has result, send to client.....");
                nativeServer.send(result);
            }
        });

        try {
            new Server(args).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() throws IOException {
        // pipelined computation
        for (int i = 10; i > 0; i--) {
            OneRoundComputation(Constants.MIDDLE_HOME + File.separator + dataFiles.get(i - 1));
        }

        // join computation thread
        JoinComputationThread();

        logger.info("JoinComputationThread finished.");

        nativeServer.finish();

        int i = 0;
        for (Map.Entry<Long, String> entry : ServerPipelinedComputation.inRangeRecord.entrySet()) {
            if (i < 10)
                logger.info(entry.getValue());
            i++;
        }
        logger.info("size:" + ServerPipelinedComputation.inRangeRecord.size());
        logger.info("Send finish all package......");
    }
}
