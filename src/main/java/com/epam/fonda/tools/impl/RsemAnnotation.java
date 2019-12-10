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

import com.epam.fonda.entity.command.BashCommand;
import com.epam.fonda.entity.configuration.Configuration;
import com.epam.fonda.tools.Tool;
import com.epam.fonda.tools.results.RsemResult;
import com.epam.fonda.utils.PipelineUtils;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@RequiredArgsConstructor
public class RsemAnnotation implements Tool<RsemResult> {

    @NonNull
    private RsemResult rsemResult;

    @Data
    private class RsemAnnotationFields {
        private String annotGeneSaf;
        private String jarPath;
        private String python;
        private String rsemGeneResult;
        private String rsemAnnoGeneResult;
    }

    private static final String RSEM_ANNOTATION_TEMPLATE_NAME = "rsem_annotation_template";

    /**
     * This method generates bash script {@link BashCommand} for RSEM annotation.
     *
     * @param configuration  is the type of {@link Configuration}.
     * @param templateEngine is the type of {@link TemplateEngine}.
     * @return {@link BashCommand} with bash script.
     */
    @Override
    public RsemResult generate(Configuration configuration, TemplateEngine templateEngine) {
        RsemAnnotationFields rsemAnnotationFields = constructFields(configuration);
        Context context = new Context();
        context.setVariable("rsemAnnotationFields", rsemAnnotationFields);
        final String cmd = templateEngine.process(RSEM_ANNOTATION_TEMPLATE_NAME, context);
        rsemResult.setCommand(BashCommand.withTool(rsemResult.getCommand().getToolCommand() + cmd));
        return rsemResult;
    }

    /**
     * This method initializes fields of the RSEM Annotation {@link RsemAnnotation} class.
     *
     * @param configuration
     * @return {@link RsemAnnotationFields} with fields
     */
    private RsemAnnotationFields constructFields(Configuration configuration) {
        checkValues(configuration);
        RsemAnnotationFields rsemExpressionFields = new RsemAnnotationFields();
        rsemExpressionFields.annotGeneSaf = configuration.getGlobalConfig().getDatabaseConfig().getAnnotgenesaf();
        rsemExpressionFields.jarPath = PipelineUtils.getExecutionPath();
        rsemExpressionFields.python = configuration.getGlobalConfig().getToolConfig().getPython();
        rsemExpressionFields.rsemGeneResult = rsemResult.getRsemOutput().getRsemGeneResult();
        rsemExpressionFields.rsemAnnoGeneResult = rsemExpressionFields.rsemGeneResult
                .replace(".gene", ".annotate.gene");
        return rsemExpressionFields;
    }

    /**
     * Method checks all needed fields of configuration for null/empty values
     *
     * @param configuration
     */
    private void checkValues(Configuration configuration) {
        String message = "Empty configuration values appeared";
        Validate.notBlank(configuration.getGlobalConfig().getDatabaseConfig().getAnnotgenesaf(), message);
        Validate.notBlank(configuration.getGlobalConfig().getToolConfig().getPython(), message);
        Validate.notBlank(rsemResult.getRsemOutput().getRsemGeneResult(), message);
    }
}