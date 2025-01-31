package io.quarkus.reactive.pg.client.deployment;

import java.util.function.Function;

import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.pgclient.PgPool;

@Deprecated(forRemoval = true)
public final class PgPoolBuildItem extends MultiBuildItem {

    public PgPoolBuildItem(String dataSourceName, Function<SyntheticCreationalContext<PgPool>, PgPool> pgPool) {
    }

    public String getDataSourceName() {
        throw new IllegalStateException("should never be called");
    }

    public Function<SyntheticCreationalContext<PgPool>, PgPool> getPgPool() {
        throw new IllegalStateException("should never be called");
    }

    public boolean isDefault() {
        throw new IllegalStateException("should never be called");
    }
}
