package io.openems.edge.meter.iometer;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(
    name = "Meter IOMeter",
    description = "Implements the IOMeter API to read meter values."
)
@interface Config {
    @AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
    String id() default "meter0";

    @AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
    String alias() default "";

    @AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
    boolean enabled() default true;

    @AttributeDefinition(name = "Base URL", description = "Base URL of the IOMeter API")
    String baseUrl() default "https://api.corrently.io/v2.0/iometer/reading";

    @AttributeDefinition(name = "JWT", description = "JWT token for authentication")
    String jwt();
}