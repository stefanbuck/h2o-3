package ai.h2o.automl.targetencoder.strategy;

import ai.h2o.automl.AutoMLBuildSpec;
import ai.h2o.automl.targetencoder.ModelParametersEvaluator;
import ai.h2o.targetencoding.TargetEncoder;
import ai.h2o.targetencoding.TargetEncoderModel;
import hex.ModelBuilder;
import hex.grid.HyperSpaceSearchCriteria;
import hex.grid.HyperSpaceWalker;
import hex.schemas.TargetEncoderV3;
import water.api.GridSearchHandler;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.HashMap;
import java.util.PriorityQueue;

/**
 *  Random grid search of optimal hyper parameters for target encoding
 */
public class GridSearchModelParametersSelectionStrategy extends ModelParametersSelectionStrategy<TargetEncoderModel.TargetEncoderParameters> {

  private ModelBuilder _modelBuilder;
  private AutoMLBuildSpec.AutoMLTEControl _teBuildSpec;

  protected HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters> _walker;

  private ModelParametersEvaluator<TargetEncoderModel.TargetEncoderParameters> _evaluator;
  private PriorityQueue<ModelParametersSelectionStrategy.Evaluated<TargetEncoderModel.TargetEncoderParameters>> _evaluatedQueue;

  protected ModelValidationMode _validationMode;
  private Frame _leaderboardData;
  protected transient String[] _columnNamesToEncode;


  public GridSearchModelParametersSelectionStrategy(ModelBuilder modelBuilder,
                                                    AutoMLBuildSpec.AutoMLTEControl teBuildSpec,
                                                    Frame leaderboard,
                                                    String[] columnNamesToEncode,
                                                    ModelValidationMode validationMode,
                                                    ModelParametersEvaluator<TargetEncoderModel.TargetEncoderParameters> evaluator) {
    _teBuildSpec = teBuildSpec;
    _modelBuilder = modelBuilder;
    _evaluator = evaluator;

    _leaderboardData = leaderboard;
    _columnNamesToEncode = columnNamesToEncode;

    //TODO what is the canonical way to get metric we are going to use. DistributionFamily, leaderboard metrics?
    boolean theBiggerTheBetter = modelBuilder._parms.train().vec(modelBuilder._parms._response_column).get_type() != Vec.T_NUM;

    _evaluatedQueue = new PriorityQueue<>(new EvaluatedComparator(theBiggerTheBetter));

    _validationMode = validationMode;


    setTESearchSpace(_validationMode);
  }

  private void setTESearchSpace(ModelValidationMode modelValidationMode) {

    HashMap<String, Object[]> grid = new HashMap<>();
    grid.put("blending", new Boolean[]{true, /*false*/}); // TODO use filtering when PUBDEV-7037 is available
    grid.put("noise_level", new Double[]{0.0, 0.01, 0.1});
    grid.put("k", new Double[]{1.0, 2.0, 3.0, 5.0, 10.0, 50.0, 100.0});
    grid.put("f", new Double[]{5.0, 10.0, 20.0});

    switch (modelValidationMode) {
      case CV:
        grid.put("data_leakage_handling", new TargetEncoder.DataLeakageHandlingStrategy[]{TargetEncoder.DataLeakageHandlingStrategy.KFold});
        break;
      case VALIDATION_FRAME:
        // TODO apply filtering. When we choose holdoutType=None we don't need to search for noise
        grid.put("data_leakage_handling", new TargetEncoder.DataLeakageHandlingStrategy[]{TargetEncoder.DataLeakageHandlingStrategy.KFold, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, TargetEncoder.DataLeakageHandlingStrategy.None});
        break;
    }

    TargetEncoderModel.TargetEncoderParameters parameters = new TargetEncoderModel.TargetEncoderParameters();

    GridSearchHandler.DefaultModelParametersBuilderFactory<TargetEncoderModel.TargetEncoderParameters, TargetEncoderV3.TargetEncoderParametersV3> modelParametersBuilderFactory =
            new GridSearchHandler.DefaultModelParametersBuilderFactory<>();

    HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();
    hyperSpaceSearchCriteria.set_seed(_teBuildSpec.seed);

    _walker = new HyperSpaceWalker.RandomDiscreteValueWalker<>(parameters, grid, modelParametersBuilderFactory, hyperSpaceSearchCriteria);

  }

  public TargetEncoderModel.TargetEncoderParameters getBestParams() {
    return getBestParamsWithEvaluation().getItem();
  }

  public ModelParametersSelectionStrategy.Evaluated<TargetEncoderModel.TargetEncoderParameters> getBestParamsWithEvaluation() {

    HyperSpaceWalker.HyperSpaceIterator<TargetEncoderModel.TargetEncoderParameters> iterator = _walker.iterator();

    long attemptIdx = 0;
    while (iterator.hasNext(null) && (_teBuildSpec.te_max_models == 0 || _evaluatedQueue.size() < _teBuildSpec.te_max_models)) {

      TargetEncoderModel.TargetEncoderParameters nextModelParameters = iterator.nextModelParameters(null);

      ModelBuilder clonedModelBuilder = ModelBuilder.make(_modelBuilder._parms);

      clonedModelBuilder.init(false);

      double evaluationResult = _evaluator.evaluate(nextModelParameters, clonedModelBuilder, _validationMode, _leaderboardData, _columnNamesToEncode, _teBuildSpec.seed);

      _evaluatedQueue.add(new Evaluated<>(nextModelParameters, evaluationResult, ++attemptIdx));
    }

    return _evaluatedQueue.peek();
  }

  public PriorityQueue<Evaluated<TargetEncoderModel.TargetEncoderParameters>> getEvaluatedModelParameters() {
    return _evaluatedQueue;
  }
}