/*
 * Copyright (C) 2016-2018 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */

package gobblin.compaction.conditions;


import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.collect.ImmutableList;

import gobblin.annotation.Alias;
import gobblin.compaction.dataset.DatasetHelper;
import gobblin.compaction.dataset.Dataset;
import gobblin.compaction.mapreduce.MRCompactor;
import gobblin.util.ClassAliasResolver;
import gobblin.util.reflection.GobblinConstructorUtils;


/**
 * An implementation {@link RecompactionCondition} which contains multiple recompact conditions.
 * An operation (AND or OR) is to combine these operations.
 */

@Alias("RecompactionCombineCondition")
public class RecompactionCombineCondition implements RecompactionCondition {
  public enum CombineOperation {
    OR,
    AND
  }
  private final List<RecompactionCondition> recompactionConditions;
  private final CombineOperation operation;

  private static final Logger logger = LoggerFactory.getLogger (RecompactionCombineCondition.class);

  public RecompactionCombineCondition (Dataset dataset) {
    this.recompactionConditions = getConditionsFromProperties (dataset);
    this.operation = getConditionOperation(dataset);


    if (this.recompactionConditions.size() == 0) {
      throw new IllegalArgumentException( "No combine conditions specified");
    }
  }

  public RecompactionCombineCondition (List<RecompactionCondition> conditions, CombineOperation opr) {
    this.recompactionConditions = conditions;
    this.operation = opr;
  }

  private CombineOperation getConditionOperation (Dataset dataset) {
    String oprName = dataset.jobProps().getProp (MRCompactor.COMPACTION_RECOMPACT_COMBINE_CONDITIONS_OPERATION,
        MRCompactor.DEFAULT_COMPACTION_RECOMPACT_COMBINE_CONDITIONS_OPERATION);

    try {
       CombineOperation opr = CombineOperation.valueOf (oprName.toUpperCase());
       return opr;
    } catch (Exception e) {
       return CombineOperation.OR;
    }
  }

  private ImmutableList<RecompactionCondition> getConditionsFromProperties (Dataset dataset) {
    ClassAliasResolver<RecompactionCondition> conditionClassAliasResolver = new ClassAliasResolver<>(RecompactionCondition.class);
    List<String> conditionNames = dataset.jobProps().getPropAsList(MRCompactor.COMPACTION_RECOMPACT_COMBINE_CONDITIONS,
        MRCompactor.DEFAULT_COMPACTION_RECOMPACT_CONDITION);

    ImmutableList.Builder<RecompactionCondition> builder = ImmutableList.builder();

    for (String conditionClassName : conditionNames) {
      try {
        builder.add(GobblinConstructorUtils.invokeFirstConstructor(
            conditionClassAliasResolver.resolveClass(conditionClassName), ImmutableList.<Object> of(dataset)));
      } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException
          | ClassNotFoundException e) {
        throw new IllegalArgumentException(e);
      }
    }
    return builder.build();
  }

  /**
   * For OR combination, return true iff one of conditions return true
   * For AND combination, return true iff all of conditions return true
   * Other cases, return false
   */
  public boolean isRecompactionNeeded (DatasetHelper helper) {
    if (recompactionConditions.isEmpty())
      return false;

    if (operation == CombineOperation.OR) {
      for (RecompactionCondition c : recompactionConditions) {
        if (c.isRecompactionNeeded(helper)) {
          return true;
        }
      }
      return false;
    } else {
      for (RecompactionCondition c : recompactionConditions) {
        if (!c.isRecompactionNeeded(helper)) {
          return false;
        }
      }
      return true;
    }
  }
}
