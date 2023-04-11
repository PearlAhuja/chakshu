package com.freecharge.smsprofilerservice.service.impl.train;

import com.freecharge.smsprofilerservice.service.ModelTrainerService;
import com.freecharge.vault.PropertiesConfig;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.namefind.NameSampleDataStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.BaseModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author parag.vyas
 */
@Service
@Slf4j
public abstract class BaseTrainerImpl implements ModelTrainerService {

    private String baseDirectoryModel;

    @Autowired
    public BaseTrainerImpl(@Qualifier("applicationProperties") PropertiesConfig propertiesConfig) {
        final Map<String, Object> awsProperties = propertiesConfig.getProperties();
        this.baseDirectoryModel = (String) awsProperties.get("model.trainer.base.directory");
    }

    @Override
    public byte[] train(String filePrefix, List<String> source) {
        ObjectStream sampleStream;
        try {
            sampleStream = new NameSampleDataStream(ObjectStreamUtils.createObjectStream(source));
            sampleStream.close();
        } catch (IOException e) {
            log.error("IO exception  in nfm", e);
            return new byte[0];
        }

        TrainingParameters params = TrainingParameters.defaultParams();
        params.put(TrainingParameters.ALGORITHM_PARAM, "MAXENT");
        params.put(TrainingParameters.TRAINER_TYPE_PARAM, EventTrainer.EVENT_VALUE);
        params.put(TrainingParameters.ITERATIONS_PARAM, 200);
        params.put(TrainingParameters.CUTOFF_PARAM, 1);
        // training the model using TokenNameFinderModel class
        BaseModel nameFinderModel = null;
        try {
            nameFinderModel = getModel(params, sampleStream);
        } catch (IOException e) {
            log.error("IO exception  in nfm", e);
            return new byte[0];
        }

        String path = String.format("%s%s%s", this.baseDirectoryModel, filePrefix, "-model.bin");
        File output = new File(path);
        try (FileOutputStream outputStream = new FileOutputStream(output)) {
            Objects.requireNonNull(nameFinderModel).serialize(outputStream);
            return Files.readAllBytes(Paths.get(path));
        } catch (FileNotFoundException e) {
            log.error("File not found exception ", e);
        } catch (IOException e) {
            log.error("IO exception  in nfm", e);
        } finally {
            try {
                Files.delete(Paths.get(path));
            } catch (IOException e) {
                log.error("Exception while deleting file {} error: {}", path, e);
            }
        }
        return new byte[0];
    }

    public abstract BaseModel getModel(TrainingParameters params, ObjectStream sampleStream) throws IOException;

}