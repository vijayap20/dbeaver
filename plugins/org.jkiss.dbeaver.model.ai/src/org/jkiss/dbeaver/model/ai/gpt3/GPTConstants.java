/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2023 DBeaver Corp and others
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
package org.jkiss.dbeaver.model.ai.gpt3;

/**
 * GPT preference constants
 */
public class GPTConstants {

    public static final String OPENAI_ENGINE = "openai";

    public static final String GPT_API_TOKEN = "gpt.token";
    public static final String GPT_MODEL = "gpt.model";
    public static final String GPT_MODEL_TEMPERATURE = "gpt.model.temperature";
    public static final String GPT_LOG_QUERY = "gpt.log.query";
        
    //Claude Connection Strings
    public static final String CLAUDEAI_ENGINE = "anthropic";
    public static final String CLAUDE_API_TOKEN = "gpt.token";
    public static final String CLAUDE_MODEL = "claude-2";
    public static final String CLAUDE_MODEL_TEMPERATURE = "gpt.model.temperature";
    public static final String CLAUDE_LOG_QUERY = "gpt.log.query";
    public static final String CLAUDE_MODEL_TOP_P = "gpt.model.topP";
    public static final String CLAUDE_MODEL_TOP_K = "gpt.model.topK";
}

