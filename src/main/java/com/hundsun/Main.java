package com.hundsun;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    private final static String TARGET_PATH = "/target/lib";

    private static Logger logger = Logger.getLogger(Main.class);

    public static void main(String[] args) {

        long start = System.currentTimeMillis();
        if (null == args || args.length == 0) {
            logger.info("args is empty");
            return;
        }
        // 第一个入参为源路径
        logger.info("first param is " + args[0]);
        if ("".equals(args[0])) {
            logger.info("args[0] is empty");
            return;
        }
        // 目标路径以逗号隔开
        String[] services = args[0].split(",");
        String webServicePath = services[0].substring(0, services[0].indexOf("WebService")) + File.separator + "WebService";
        // 第二个入参为输出路径
        String outPath = ((args.length == 1) ? webServicePath : args[1]) + File.separator + "shared";
        // filename目录，用于存放每个微服务节点的依赖jar清单
        String outFileNamePath = outPath + File.separator + "filename" + File.separator;
        // lib目录，用于存放所有微服务节点的依赖jar
        String outLibPath = outPath + File.separator + "lib"  + File.separator;
        File outFile = new File(outPath);
        File outFileNameFile = new File(outFileNamePath);
        File outLibFile = new File(outLibPath);
        try {
            // 删除顶层目录
            delDir(outPath);
            logger.info("del dir " + outPath + " suc");
            // 新建目录
            outFile.mkdir();
            outFileNameFile.mkdir();
            outLibFile.mkdir();

            // 遍历所有待处理的服务
            for (int i = 0; i < services.length; i ++) {
                String path;
                String service = services[i];
                if (service.endsWith(File.separator)) {
                    path = services[i].substring(0, services[i].length() - 1);
                } else {
                    path = services[i];
                }
                // 锁定target/lib目录
                path = path + TARGET_PATH;
                File file = new File(path);
                if (!file.exists()) {
                    logger.info(path + " not existed");
                    continue;
                }

                if (file.isDirectory()) {
                    // 获取下面的子目录,例如.../target/lib/hui这层
                    File[] files = file.listFiles();
                    String serviceName = files[0].getName();
                    // jar依赖清单列表文件
                    File serviceJarListFile = new File(outFileNamePath + serviceName + ".txt");
                    createFile(serviceJarListFile);
                    // 获取当前目录下的所有jar文件
                    if (files[0].isDirectory()) {
                        File[] singleServiceJarList = files[0].listFiles();
                        logger.info(String.format("cur service is %s & jar dependency count is %s ", serviceName, singleServiceJarList.length));
                        List<String> jarNameList = new ArrayList<String>();
                        // 遍历所有的jar
                        for (int j = 0; j < singleServiceJarList.length; j ++) {
                            // jar名称
                            String jarName = singleServiceJarList[j].getName();
                            jarNameList.add(jarName);
                            // 将当前jar复制到shared/lib目录
                            FileUtils.copyFileToDirectory(singleServiceJarList[j], outLibFile);
                        }
                        // 将当前服务的jar依赖名称写到依赖清单文件
                        FileUtils.writeLines(serviceJarListFile, jarNameList, true);
                    }
                }
            }
            // 处理shared/lib下的jar,检查是否存在多版本的jar依赖
            File[] allJarFiles = outLibFile.listFiles();
            Map<String, String> uniqueJarMap = new HashMap<String, String>();
            // 唯一版本的jar依赖
            List<String> singleNameList = new ArrayList<String>();
            // 在uniqueJarMap存在的jar
            List<String> existedNameList = new ArrayList<String>();
            // 多版本的jar
            List<String> multipleNameList = new ArrayList<String>();
            // 记录多版本jar文件
            File dupFile = new File(outFileNamePath + "/" + "dup.txt");
            // 记录单一版本jar文件
            File uniqueFile = new File(outFileNamePath + "/" + "unique.txt");
            createFile(dupFile);
            createFile(uniqueFile);
            // 遍历所有jar
            for (int i = 0; i < allJarFiles.length; i ++) {
                String jarName = allJarFiles[i].getName();
                String noVersionName = jarName.substring(0, jarName.lastIndexOf("-"));
                if (jarName.contains("SNAPSHOT")) {
                    logger.info("cur SNAPSHOT jar is " + jarName);
                    noVersionName = noVersionName.substring(0, noVersionName.lastIndexOf("-"));
                }
                if (!uniqueJarMap.containsKey(noVersionName)) {
                    uniqueJarMap.put(noVersionName, jarName);
                    singleNameList.add(jarName);
                } else {
                    existedNameList.add(noVersionName);
                    multipleNameList.add(jarName);
                }
            }
            // 将在singleNameList里的jar版本找出来放到multipleNameList并删除
            for (int i = 0; i < existedNameList.size(); i ++) {
                String key = existedNameList.get(i);
                String existedName = uniqueJarMap.get(key);
                multipleNameList.add(existedName);
                if (singleNameList.contains(existedName)) {
                    singleNameList.remove(existedName);
                }
            }
            // 写到dupFile文件
            FileUtils.writeLines(dupFile, multipleNameList, true);
            // 写到uniqueFile文件
            FileUtils.writeLines(uniqueFile, singleNameList, true);
            logger.info(String.format("Result Total : %s, Unique : %s, Dup : %s ",
                    allJarFiles.length, singleNameList.size(), multipleNameList.size()));
            if (multipleNameList.size() == 0) {
                logger.info("no dup jar existed");
            } else {
                logger.info("there are " + multipleNameList.size() + " dup jar existed");
            }
            logger.info("Total time is " + (System.currentTimeMillis() - start) + " ms");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 创建文件
     * @param file
     */
    public static void createFile(File file) {
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 删除文件夹
     * @param filePath
     */
    public static void delDir(String filePath){
        File file = new File(filePath);
        if(!file.exists()){
            return;
        }

        if(file.isFile()){
            file.delete();
        } else if (file.isDirectory()){
            File[] files = file.listFiles();
            for (File subFile : files) {
                // 递归删除
                delDir(filePath + File.separator + subFile.getName());
            }

            file.delete();
        }
    }
}
