package hex.schemas;

import ai.h2o.targetencoding.ColumnsMapping;
import ai.h2o.targetencoding.TargetEncoderModel;
import water.api.API;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.SchemaV3;


public class TargetEncoderModelOutputV3 extends ModelOutputSchemaV3<TargetEncoderModel.TargetEncoderOutput, TargetEncoderModelOutputV3> {
    
    public static class ColumnsMappingV3 extends SchemaV3<ColumnsMapping, ColumnsMappingV3> {
        String[] from;
        String[] to;
    }
    
    @API(help = "Mapping between input column(s) and their corresponding target encoded output column(s). " +
            "Please note that there can be multiple columns on the input/from side if columns grouping was used, " +
            "and there can also be multiple columns on the output/to side if the target was multiclass.",
            direction = API.Direction.OUTPUT)
    ColumnsMappingV3[] input_to_output_columns;
}

