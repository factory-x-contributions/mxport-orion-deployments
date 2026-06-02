package org.eclipse.edc.industrial.connector.resolver.datatypes;

import java.util.Set;

public interface IndustrialConnectorDataTypes {
    Set<String> getSupportedDataTypes();

    Boolean isSupported(String dataType);
}
