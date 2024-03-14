package com.laudspeaker.android;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;

public class LaudspeakerApi {
    private final LaudspeakerConfig config;
    private final MediaType mediaType;
    private final OkHttpClient client;

    public LaudspeakerApi(LaudspeakerConfig config) {
        this.config = config;
        this.mediaType = MediaType.parse("application/json; charset=utf-8");
        this.client = new OkHttpClient.Builder().build();
    }

    private String getTheHost() {
        if (config.getHost().endsWith("/")) {
            return config.getHost().substring(0, config.getHost().length() - 1);
        } else {
            return config.getHost();
        }
    }

    public void send(List<LaudspeakerEvent> events) throws LaudspeakerApiError, IOException {
        LaudspeakerBatchEvent batch = new LaudspeakerBatchEvent(events);
        batch.setSentAt(config.getDateProvider().currentDate());

        Request request = makeRequest(getTheHost() + "/events/batch/", outputStream -> {
            OutputStreamWriter writer = new OutputStreamWriter(outputStream);
            config.getSerializer().toJson(batch, writer);
            writer.flush();
        });

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful())
                throw new LaudspeakerApiError(response.code(), response.message(), response.body());
        }
    }

    private Request makeRequest(String url, IOConsumer<OutputStream> serializer) throws IOException {
        RequestBody requestBody = new RequestBody() {
            @Override
            public MediaType contentType() {
                return mediaType;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                OutputStream outputStream = sink.outputStream();
                serializer.accept(outputStream);
                outputStream.flush();
            }
        };

        return new Request.Builder().url(url).header("Authorization", "Api-Key " + config.getApiKey()).header("User-Agent", config.getUserAgent()).post(requestBody).build();
    }

    @FunctionalInterface
    public interface IOConsumer<T> {
        void accept(T t) throws IOException;
    }
}
