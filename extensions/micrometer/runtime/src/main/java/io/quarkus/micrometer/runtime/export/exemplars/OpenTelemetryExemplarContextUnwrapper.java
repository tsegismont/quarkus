package io.quarkus.micrometer.runtime.export.exemplars;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.quarkus.opentelemetry.runtime.QuarkusContextStorage;
import jakarta.enterprise.context.Dependent;

import java.util.Objects;
import java.util.function.Function;

@Dependent
public class OpenTelemetryExemplarContextUnwrapper implements OpenTelemetryContextUnwrapper {

    @Override
    public <P, R> R executeInContext(Function<P, R> methodReference, P parameter, io.vertx.core.Context requestContext) {
        if (requestContext == null) {
            return methodReference.apply(parameter);
        }

        Context newContext = QuarkusContextStorage.getContext(requestContext);

        if (newContext == null) {
            return methodReference.apply(parameter);
        }

        io.opentelemetry.context.Context expected = QuarkusContextStorage.INSTANCE.current();
        R applied;
        try (Scope ignored = QuarkusContextStorage.INSTANCE.attach(newContext)) {
            assert QuarkusContextStorage.INSTANCE.current() != null;
            applied = methodReference.apply(parameter);
        }
        assert Objects.equals(expected, QuarkusContextStorage.INSTANCE.current());
        return applied;
    }
}
