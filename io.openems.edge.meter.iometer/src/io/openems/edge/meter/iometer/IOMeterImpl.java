package io.openems.edge.meter.iometer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.common.types.MeterType;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@Designate(ocd = Config.class, factory = true)
@Component(//
    name = "Meter.IOMeter", //
    immediate = true, //
    configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class IOMeterImpl extends AbstractOpenemsComponent implements ElectricityMeter, OpenemsComponent {

    private final Logger log = LoggerFactory.getLogger(IOMeterImpl.class);
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final HttpClient httpClient;

    @Reference
    protected ConfigurationAdmin cm;

    private Config config;
    private String baseUrl;
    private String jwt;

    public IOMeterImpl() {
        super(//
            OpenemsComponent.ChannelId.values(), //
            ElectricityMeter.ChannelId.values());
        
        // Initialize HTTP client with timeouts
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Activate
    void activate(ComponentContext context, Config config) {
        super.activate(context, config.id(), config.alias(), config.enabled());
        this.config = config;
        this.baseUrl = config.baseUrl();
        this.jwt = config.jwt();
        
        // Schedule regular updates
        executor.scheduleWithFixedDelay(() -> {
            try {
                update();
            } catch (Exception e) {
                this.log.error("Error while updating values: " + e.getMessage());
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    @Deactivate
    protected void deactivate() {
        executor.shutdown();
        super.deactivate();
    }

    private void update() {
        try {
            String url = this.baseUrl + "?token=" + this.jwt;
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
            
            // Synchronous HTTP call
            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                processResponse(response.body());
            } else {
                log.error("HTTP error: " + response.statusCode());
            }
            
        } catch (Exception e) {
            log.error("Failed to fetch meter data: " + e.getMessage());
        }
    }
    
    private void processResponse(String jsonString) {
        try {
            JsonArray readings = JsonParser.parseString(jsonString).getAsJsonArray();
            if (readings.size() == 0) {
                log.warn("No readings received from IOMeter API");
                return;
            }
            
            // Use the most recent reading (last element in array)
            JsonObject latestReading = readings.get(readings.size() - 1).getAsJsonObject();
            JsonObject values = latestReading.getAsJsonObject("values");
            
            // Extract and set values
            if (values.has("energy")) {
                this.channel(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY)
                    .setNextValue(values.get("energy").getAsLong());
            }
            
            if (values.has("energyOut")) {
                this.channel(ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY)
                    .setNextValue(values.get("energyOut").getAsLong());
            }
            
            if (values.has("power")) {
                this.channel(ElectricityMeter.ChannelId.ACTIVE_POWER)
                    .setNextValue(values.get("power").getAsInt());
            }
            
        } catch (Exception e) {
            log.error("Failed to process JSON response: " + e.getMessage());
        }
    }

    @Override
    public String debugLog() {
        return "L:" + this.getActivePower().asString();
    }

    @Override
    public MeterType getMeterType() {
        return MeterType.GRID;
    }
}