package com.icc.qasker.quiz.dto.aiResponse;


import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(
    use = Id.NAME,
    include = As.PROPERTY,
    property = "type",
    visible = true
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = ProblemSetGeneratedEvent.class, name = "quiz"),
    @JsonSubTypes.Type(value = ErrorEvent.class, name = "error")
})
public interface StreamEvent {

}


