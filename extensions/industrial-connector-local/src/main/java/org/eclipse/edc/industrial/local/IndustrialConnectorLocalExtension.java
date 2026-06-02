package org.eclipse.edc.industrial.local;

import org.eclipse.edc.common.spi.config.mosquitto.IndustrialConnectorResolverConfigServiceImpl;
import org.eclipse.edc.common.spi.dataflow.TransferFlowService;
import org.eclipse.edc.industrial.local.dataflow.IndustrialConnectorLocalTransferFlowProvisionerServiceImpl;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import static org.eclipse.edc.industrial.local.IndustrialConnectorLocalExtension.NAME;

@Provides(TransferFlowService.class)
@Extension(value = NAME)
public class IndustrialConnectorLocalExtension implements ServiceExtension {
    public static final String NAME = "Local Extension with OPC UA via MQTT";

    // Add configuration settings for conditional loading
    private static final String EXTENSION_ENABLED = "edc.industrial.connector.wss.enabled";

    @Override
    public String name() {
        return "Industrial Connector Local Extension";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();

        // Check if extension should be enabled
        boolean wssExtensionEnabled = context.getSetting(EXTENSION_ENABLED, false);
        if (wssExtensionEnabled) {
            monitor.info("Local Extension is disabled via configuration");
            return;
        }

        var configServiceImplementation = new IndustrialConnectorResolverConfigServiceImpl(context.getConfig());

        var transferFlowServiceProvisioner = new IndustrialConnectorLocalTransferFlowProvisionerServiceImpl(monitor, configServiceImplementation);
        var transferFlowService = transferFlowServiceProvisioner.getTransferFlowService();
        context.registerService(TransferFlowService.class, transferFlowService);
        monitor.info("Registered TransferFlowService provisioner for OPC UA MQTT with Mosquitto broker");
    }
}
