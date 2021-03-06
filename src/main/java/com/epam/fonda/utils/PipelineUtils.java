/*
 * Copyright 2017-2020 Sanofi and EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.fonda.utils;

import com.epam.fonda.entity.command.AbstractCommand;
import com.epam.fonda.entity.command.BashCommand;
import com.epam.fonda.entity.configuration.Configuration;
import com.epam.fonda.entity.configuration.EOLMarker;
import com.epam.fonda.samples.fastq.FastqFileSample;
import com.epam.fonda.tools.results.FastqOutput;
import com.epam.fonda.tools.results.FastqResult;
import com.epam.fonda.workflow.TaskContainer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.epam.fonda.Executor.execute;
import static com.epam.fonda.entity.configuration.EOLMarker.CRLF;
import static com.epam.fonda.entity.configuration.EOLMarker.LF;
import static java.lang.String.format;

@Slf4j
public final class PipelineUtils {
    public static final TemplateEngine TEMPLATE_ENGINE = TemplateEngineUtils.init();

    public static final String NA = "NA";
    public static final String CASE = "case";
    public static final String TUMOR = "tumor";

    private static final String FASTQS_1_NAME = "fastqs1";
    private static final String FASTQS_2_NAME = "fastqs2";
    private static final String MERGED_FASTQ_1_NAME = "mergedFastq1";
    private static final String MERGED_FASTQ_2_NAME = "mergedFastq2";
    private static final String JOB_FINISH = "echo `date` Finish the job execution!\n";
    private static final String VARIABLES_MAP = "variablesMap";
    private static final String SLASH = "/";

    private PipelineUtils() {
    }

    private static final String STATIC_SHELL_TEMPLATE_NAME = "static_shell_template";
    private static final String ADD_TASK_TEMPLATE_NAME = "add_task_template";
    private static final String CLEAN_UP_TMPDIR_TEMPLATE_NAME = "clean_up_tmpDir_template";
    private static final String MERGE_FASTQ_TEMPLATE_NAME = "merge_fastq_template";

    public static final int ERROR_STATUS = 1;

    /**
     * This method generates jar path for pipeline tools.
     *
     * @return absolute path of type {@link String}
     **/
    public static String getExecutionPath(final Configuration configuration) {
        if (StringUtils.isNotBlank(configuration.getGlobalConfig().getToolConfig().getSrcPath())) {
            final String srcPath = configuration.getGlobalConfig().getToolConfig().getSrcPath();
            if (srcPath.endsWith(SLASH)) {
                return StringUtils.chop(srcPath);
            }
            return srcPath;
        }
        final String executionPath = PipelineUtils.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        final String path = executionPath.substring(0, executionPath.lastIndexOf(SLASH));
        try {
            return URLDecoder.decode(path, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            log.error("Could not decode execution path: " + executionPath);
        }
        return path;
    }

    /**
     * This method creates script and writes it into the file.
     *
     * @param configuration is the type of {@link Configuration} which contains
     *                      its fields: workflow, local, numThreads, pe, queue.
     * @param task          is the type or {@link String} and contains the name of the task.
     * @param cmd           is the type or {@link String} and contains the bash script.
     * @param sampleName    sample name
     * @throws IOException throws when file cannot be written or be created properly
     **/
    public static void createStaticShell(Configuration configuration, String task,
                                         String cmd, String sampleName) throws IOException {
        cmd += JOB_FINISH;
        Map<String, String> variablesMap = initializeVariablesMap(configuration, sampleName, task);
        Context context = new Context();
        context.setVariable(VARIABLES_MAP, variablesMap);
        String staticShell = TEMPLATE_ENGINE.process(STATIC_SHELL_TEMPLATE_NAME, context);
        writeToFile(String.valueOf(variablesMap.get("shellToSubmit")), staticShell + cmd,
                configuration.getGlobalConfig().getPipelineInfo().getLineEnding());
    }

    /**
     * This method writes into the file.
     *
     * @param path   is the type or {@link String} and contains the path to the file.
     * @param source is the type or {@link String} and contains the file itself.
     * @throws IOException throws when file cannot be written or be created properly
     **/
    public static void writeToFile(final String path, final String source, final EOLMarker eol) throws IOException {
        Files.write(Paths.get(path), normalize(source, eol).getBytes());
    }

    /**
     * This method adds task name to the script.
     *
     * @param configuration is the type of {@link Configuration} which contains
     *                      its fields: workflow, local, numThreads, pe, queue.
     * @param task          is the type or {@link String} and contains the name of the task.
     * @param sampleName    sample name
     * @return resulting script of type {@link String}
     **/
    public static String addTask(Configuration configuration, String task, String sampleName) {
        Map<String, String> variablesMap = initializeVariablesMap(configuration, sampleName, task);
        Context context = new Context();
        context.setVariable(VARIABLES_MAP, variablesMap);
        TaskContainer.addTasks(format("Run %s", task));
        return TEMPLATE_ENGINE.process(ADD_TASK_TEMPLATE_NAME, context);
    }

    /**
     * This method writes temporary directories names to the script.
     *
     * @param fields is the type or {@link List<String>} and contains directories paths to remove.
     * @return resulting script of type {@link String}
     **/
    public static String cleanUpTmpDir(Set<String> fields) {
        Context context = new Context();
        context.setVariable("fields", fields);
        TaskContainer.addTasks("Remove temporary directories");
        return TEMPLATE_ENGINE.process(CLEAN_UP_TMPDIR_TEMPLATE_NAME, context);
    }

    /**
     * This method creates script, writes it into the file and executes it.
     *
     * @param configuration is the type of {@link Configuration} which contains
     *                      its fields: workflow, local, numThreads, pe, queue.
     * @param cmd           is the type or {@link String} and contains the bash script.
     * @param sampleName    sample name
     * @throws IOException throws when file cannot be written or be created properly
     */
    public static void printShell(Configuration configuration, String cmd, String sampleName, String index)
            throws IOException {
        Map<String, String> variablesMap = initializeVariablesMap(configuration, sampleName,
                configuration.getCustTask());
        String workflow = configuration.getGlobalConfig().getPipelineInfo().getWorkflow();
        String custTask = configuration.getCustTask();
        String fileName = StringUtils.isNotBlank(sampleName)
                ? constructFilenameIfSampleNotNull(sampleName, workflow, custTask, index)
                : format("%s_%s_for_cohort_analysis", workflow, custTask);
        String shellToSubmit = format("%s/%s.sh", configuration.getCommonOutdir().getShOutdir(), fileName);
        variablesMap.put("fileName", fileName);
        Context context = new Context();
        context.setVariable(VARIABLES_MAP, variablesMap);
        String staticShell = TEMPLATE_ENGINE.process(STATIC_SHELL_TEMPLATE_NAME, context);

        writeToFile(shellToSubmit, format("%s%s%s", staticShell, cmd, JOB_FINISH),
                configuration.getGlobalConfig().getPipelineInfo().getLineEnding());

        if (configuration.isTestMode()) {
            return;
        }

        if (configuration.isLocalMode()) {
            execute(format("sh %s", shellToSubmit));
            return;
        }
        execute(format("qsub %s", shellToSubmit));
    }

    /**
     * This method merges fastq files.
     *
     * @param sample is the type of {@link FastqFileSample} which contains its name field.
     * @return result of merging {@link String}
     **/
    public static FastqResult mergeFastq(final FastqFileSample sample) {
        String sampleName = sample.getName();
        String sfqOutdir = sample.getFastqOutdir();
        List<String> fastqs1 = sample.getFastq1();
        List<String> fastqs2 = sample.getFastq2();
        String mergedFastq1 = "";
        String mergedFastq2 = "";
        String cmd = "";
        if (fastqs1 != null) {
            Context context = new Context();
            mergedFastq1 = format("%s/%s.merged_R1.fastq.gz", sfqOutdir, sampleName);
            context.setVariable(MERGED_FASTQ_1_NAME, mergedFastq1);
            if (fastqs2 != null) {
                mergedFastq2 = format("%s/%s.merged_R2.fastq.gz", sfqOutdir, sampleName);
                context.setVariable(MERGED_FASTQ_2_NAME, mergedFastq2);
            }
            context.setVariable(FASTQS_1_NAME, fastqs1);
            if (fastqs2 != null) {
                context.setVariable(FASTQS_2_NAME, fastqs2);
            }
            cmd = TEMPLATE_ENGINE.process(MERGE_FASTQ_TEMPLATE_NAME, context);
            TaskContainer.addTasks("Merge fastqs");
        }
        FastqOutput fastqOutput = FastqOutput.builder()
                .mergedFastq1(mergedFastq1)
                .mergedFastq2(mergedFastq2)
                .build();
        AbstractCommand command = BashCommand.withTool(cmd);
        command.setTempDirs(Collections.singletonList(sample.getTmpOutdir()));
        return FastqResult.builder()
                .command(command)
                .out(fastqOutput)
                .build();
    }

    /**
     * Method checks is directory exist, if not then create new one
     *
     * @param dir path to the directory
     * @return true if directory was created, false otherwise
     */
    public static boolean createDir(String dir) {
        Path directory = Paths.get(dir);
        if (!directory.toFile().exists()) {
            try {
                Files.createDirectory(directory);
            } catch (IOException e) {
                log.error(format("Could not create directory %s", dir), e);
            }
            return true;
        }
        return false;
    }

    /**
     * This method initializes map with values to be passed to the Thymeleaf context.
     *
     * @param configuration is the type of {@link Configuration} which contains
     *                      its fields: workflow, local, numThreads, pe, queue.
     * @param sampleName    is the type of {@link String} and contains the name of the sample.
     * @param task          is the type of {@link String} and contains the name of the task.
     * @return resulting map of type {@link Map}
     **/
    private static Map<String, String> initializeVariablesMap(Configuration configuration, String sampleName,
                                                              String task) {
        String workflow = configuration.getGlobalConfig().getPipelineInfo().getWorkflow();
        String shOutdir = configuration.getCommonOutdir().getShOutdir();
        String fileName = format("%s_%s_for_%s_analysis", workflow, task, sampleName);
        Map<String, String> variablesMap = new HashMap<>();
        variablesMap.put("shellToSubmit", format("%s/%s.sh", shOutdir, fileName));
        variablesMap.put("local", String.valueOf(configuration.isLocalMode()));
        variablesMap.put("fileName", fileName);
        variablesMap.put("task", task);
        variablesMap.put("numThreads", String.valueOf(configuration.getGlobalConfig().getQueueParameters()
                .getNumThreads()));
        variablesMap.put("queue", configuration.getGlobalConfig().getQueueParameters().getQueue());
        variablesMap.put("pe", configuration.getGlobalConfig().getQueueParameters().getPe());
        variablesMap.put("outdir", configuration.getCommonOutdir().getRootOutdir());
        variablesMap.put("sync", String.valueOf(configuration.isSyncMode()));
        return variablesMap;
    }

    /**
     * Checks control sample.
     * If control sample is not NA, it means the case sample has a matched control, then run paired mode analysis.
     *
     * @param sampleControlName
     * @return true if control sample is not NA and not blank
     */
    public static boolean isPaired(final String sampleControlName) {
        return StringUtils.isNoneBlank(sampleControlName) && !PipelineUtils.NA.equals(sampleControlName);
    }

    private static String constructFilenameIfSampleNotNull(String sampleName, String workflow, String custTask,
                                                           String index) {
        return StringUtils.isNotBlank(index)
                ? format("%s_%s_for_%s_%s_analysis", workflow, custTask, sampleName,
                index)
                : format("%s_%s_for_%s_analysis", workflow, custTask, sampleName);
    }

    /**
     * @param type contains the type of the sample.
     * @return the result of checking.
     */
    public static boolean checkSampleType(String type) {
        return TUMOR.equals(type) || CASE.equals(type);
    }

    private static String normalize(final String string, final EOLMarker eol) {
        return string.replace(System.lineSeparator(), eol.getLineSeparator())
                .replace("\r", eol == CRLF ? "\r" : LF.getLineSeparator());
    }
}
