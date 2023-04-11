package com.freecharge.smsprofilerservice.service.impl.train;

import com.freecharge.vault.PropertiesConfig;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.namefind.BioCodec;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.BaseModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;

/**
 * @author parag.vyas
 */
@Service
@Slf4j
public class NameFinderModelTrainer extends BaseTrainerImpl {

    public NameFinderModelTrainer(@Qualifier("applicationProperties") PropertiesConfig propertiesConfig) {
        super(propertiesConfig);
    }

    @Override
    public BaseModel getModel(TrainingParameters params, ObjectStream sampleStream) throws IOException {
        log.info("getModel started");
        try {
            return NameFinderME.train("en", null, sampleStream,
                    params, TokenNameFinderFactory.create(null, null, Collections.emptyMap(), new BioCodec()));
        } catch (IOException e) {
            log.error("Exception occurred getModel", e);
            throw new IOException(e.getMessage());
        }
    }
}