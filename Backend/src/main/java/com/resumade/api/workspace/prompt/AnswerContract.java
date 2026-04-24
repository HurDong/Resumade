package com.resumade.api.workspace.prompt;

import java.util.List;

public record AnswerContract(
        List<String> mustInclude,
        List<String> mustNotOverdo,
        List<String> successCriteria
) {
    public AnswerContract {
        mustInclude = mustInclude == null ? List.of() : List.copyOf(mustInclude);
        mustNotOverdo = mustNotOverdo == null ? List.of() : List.copyOf(mustNotOverdo);
        successCriteria = successCriteria == null ? List.of() : List.copyOf(successCriteria);
    }
}
