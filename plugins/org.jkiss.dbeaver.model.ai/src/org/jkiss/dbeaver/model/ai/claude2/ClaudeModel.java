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
package org.jkiss.dbeaver.model.ai.claude2;

import org.jkiss.code.NotNull;

import java.util.Arrays;
import java.util.Optional;

public enum ClaudeModel {
    CLAUDE_INSTANT("claude-instant-1", 4096, true),
    CLAUDE("claude-2", 4096, true);

    private final String name;
    private final int maxTokens;
    private final boolean isChatAPI;

    /**
     * Gets GPT model by name
     */
    @NotNull
    public static ClaudeModel getByName(@NotNull String name) {
        Optional<ClaudeModel> model = Arrays.stream(values()).filter(it -> it.name.equals(name)).findFirst();
        return model.orElse(CLAUDE);
    }

    ClaudeModel(String name, int maxTokens, boolean isChatAPI) {
        this.name = name;
        this.maxTokens = maxTokens;
        this.isChatAPI = isChatAPI;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public boolean isChatAPI() {
        return isChatAPI;
    }

    public String getName() {
        return name;
    }
}
