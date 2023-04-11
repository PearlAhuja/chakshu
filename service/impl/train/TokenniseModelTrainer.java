package com.freecharge.smsprofilerservice.service.impl.train;

import com.freecharge.vault.PropertiesConfig;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.BaseModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * @author parag.vyas
 */
@Service
@Slf4j
public class TokenniseModelTrainer extends BaseTrainerImpl {

    public TokenniseModelTrainer(@Qualifier("applicationProperties") PropertiesConfig propertiesConfig) {
        super(propertiesConfig);
    }

    @Override
    public BaseModel getModel(TrainingParameters params, ObjectStream sampleStream) throws IOException {
        return null;
    }
}