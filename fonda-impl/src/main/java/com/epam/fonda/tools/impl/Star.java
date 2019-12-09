/*
 * Copyright 2017-2019 Sanofi and EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.fonda.tools.impl;

import com.epam.fonda.entity.command.AbstractCommand;
import com.epam.fonda.entity.command.BashCommand;
import com.epam.fonda.entity.configuration.Configuration;
import com.epam.fonda.samples.fastq.FastqFileSample;
import com.epam.fonda.tools.Tool;
import com.epam.fonda.tools.results.BamOutput;
import com.epam.fonda.tools.results.BamResult;
import com.epam.fonda.workflow.impl.Flag;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Arrays;

import static java.lang.String.format;

@RequiredArgsConstructor
@Data
public class Star implements Tool<BamResult> {

    @Data
    private class ToolFields {
        private String star;
        private String java;
        private String picard;
        private String samtools;
    }

    @Data
    private class AdditionalStarFields {
        private String sbamOutdir;
        private String sampleName;
        private String unsortedBam;
        private String unsortedBamIndex;
        private String bamIndex;
        private String fastq1;
        private String fastq2;
        private String rg;
        private String index;
        private String annotgene;
        private int numThreads;
    }

    private static final String STAR_TOOL_TEMPLATE_NAME = "star_tool_template";

    @NonNull
    private Flag flag;
    @NonNull
    private FastqFileSample sample;
    @NonNull
    private BamResult bamResult;

    /**
     * This method generates bash script {@link BashCommand} for Star tool.
     *
     * @param configuration  is the type of {@link Configuration} which contains
     *                       its fields: star, annotgene, samtools, picard, numThreads.
     * @param templateEngine is the type of {@link TemplateEngine}.
     * @return {@link BashCommand} with bash script.
     **/
    @Override
    public BamResult generate(Configuration configuration, TemplateEngine templateEngine) {
        AdditionalStarFields additionalStarFields = initializeAdditionalFields(configuration);
        final String genomeBam = format("%s/%s.star.sorted.bam", sample.getBamOutdir(), sample.getName());
        final String transcriptomeBam = format("%s/%s.Aligned.toTranscriptome.out.bam", sample.getBamOutdir(),
                sample.getName());
        if (additionalStarFields.fastq1 == null) {
            throw new IllegalArgumentException(
            "Error Step: In star: no fastq files are properly provided, please check!");
        }
        Context context = new Context();
        context.setVariable("additionalStarFields", additionalStarFields);
        context.setVariable("toolFields", initializeToolFields(configuration));
        context.setVariable("flag", flag);
        context.setVariable("bam", genomeBam);
        String cmd = templateEngine.process(STAR_TOOL_TEMPLATE_NAME, context);
        BamOutput bamOutput = bamResult.getBamOutput();
        if (flag.isRsem()) {
            bamOutput.setBam(transcriptomeBam);
        } else {
            bamOutput.setBam(genomeBam);
            bamOutput.setBamIndex(additionalStarFields.bamIndex);
            bamOutput.setSortedBam(genomeBam);
            bamOutput.setSortedBamIndex(additionalStarFields.bamIndex);
            bamOutput.setUnsortedBam(additionalStarFields.unsortedBam);
            bamOutput.setUnsortedBamIndex(additionalStarFields.unsortedBamIndex);
        }
        AbstractCommand resultCommand = bamResult.getCommand();
        resultCommand.setToolCommand(resultCommand.getToolCommand() + cmd);
        resultCommand.setTempDirs(Arrays.asList(bamOutput.getSortedBam(), bamOutput.getSortedBamIndex(),
                bamOutput.getUnsortedBam(), bamOutput.getUnsortedBamIndex()));
        return BamResult.builder()
                .bamOutput(bamOutput)
                .command(resultCommand)
                .build();
    }

    /**
     * This method initializes fields of the AdditionalStarFields {@link AdditionalStarFields} class.
     *
     * @param configuration is the type of {@link Configuration} which contains
     *                      its fields: numThreads, index, annotgene.
     * @return {@link AdditionalStarFields} with its fields.
     **/
    private AdditionalStarFields initializeAdditionalFields(Configuration configuration) {
        AdditionalStarFields additionalStarFields = new AdditionalStarFields();
        additionalStarFields.sbamOutdir = sample.getBamOutdir();
        additionalStarFields.numThreads = configuration.getGlobalConfig().getQueueParameters().getNumThreads();
        additionalStarFields.sampleName = sample.getName();
        additionalStarFields.unsortedBam = format("%s/%s.Aligned.out.bam", sample.getBamOutdir(),
                sample.getName());
        additionalStarFields.unsortedBamIndex = format("%s/%s.Aligned.out.bam.bai", sample.getBamOutdir(),
                sample.getName());
        additionalStarFields.bamIndex = format("%s/%s.star.sorted.bam.bai", sample.getBamOutdir(),
                sample.getName());
        additionalStarFields.fastq1 = bamResult.getFastqOutput().getMergedFastq1();
        additionalStarFields.fastq2 = bamResult.getFastqOutput().getMergedFastq2();
        additionalStarFields.rg = format("ID:%s SM:%s LB:RNA PL:Illumina CN:cr", sample.getName(), sample
                .getName());
        additionalStarFields.index = configuration.getGlobalConfig().getDatabaseConfig().getStarIndex();
        additionalStarFields.annotgene = configuration.getGlobalConfig().getDatabaseConfig().getAnnotgene();
        return additionalStarFields;
    }

    /**
     * This method initializes fields of the ToolFields {@link ToolFields} class.
     *
     * @param configuration is the type of {@link Configuration} which contains
     *                      its fields: samtools, picard, java, star.
     * @return {@link ToolFields} with its fields.
     **/
    private ToolFields initializeToolFields(Configuration configuration) {
        ToolFields toolFields = new ToolFields();
        toolFields.java = configuration.getGlobalConfig().getToolConfig().getJava();
        toolFields.picard = configuration.getGlobalConfig().getToolConfig().getPicard();
        toolFields.samtools = configuration.getGlobalConfig().getToolConfig().getSamTools();
        toolFields.star = configuration.getGlobalConfig().getToolConfig().getStar();
        return toolFields;
    }
}
