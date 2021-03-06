/*
 * Copyright 2005 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.modelcompiler.consequence;

import org.drools.core.WorkingMemory;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.definitions.rule.impl.RuleImpl;
import org.drools.core.reteoo.RuleTerminalNode;
import org.drools.core.rule.Declaration;
import org.drools.core.spi.Consequence;
import org.drools.core.spi.KnowledgeHelper;
import org.drools.core.spi.Tuple;
import org.drools.model.Drools;
import org.drools.model.Variable;
import org.drools.model.functions.FunctionN;
import org.drools.modelcompiler.RuleContext;

public class LambdaConsequence implements Consequence {

    private final org.drools.model.Consequence consequence;
    private final RuleContext context;

    public LambdaConsequence( org.drools.model.Consequence consequence, RuleContext context ) {
        this.consequence = consequence;
        this.context = context;
    }

    @Override
    public String getName() {
        return RuleImpl.DEFAULT_CONSEQUENCE_NAME;
    }

    @Override
    public void evaluate( KnowledgeHelper knowledgeHelper, WorkingMemory workingMemory ) throws Exception {
        Declaration[] declarations = ((RuleTerminalNode)knowledgeHelper.getMatch().getTuple().getTupleSink()).getRequiredDeclarations();

        Tuple tuple = knowledgeHelper.getTuple();
        Object[] facts = new Object[declarations.length];
        for (int i = 0; i < declarations.length; i++) {
            InternalFactHandle fh = tuple.get( declarations[i] );
            facts[i] = declarations[i].getValue( (InternalWorkingMemory) workingMemory, fh.getObject() );
        }

        consequence.getBlock().execute( facts );

        Object[] objs = knowledgeHelper.getTuple().toObjects();

        for ( org.drools.model.Consequence.Update update : consequence.getUpdates() ) {
            Object updatedFact = context.getBoundFact( update.getUpdatedVariable(), objs );
            // TODO the Update specs has the changed fields so use update(FactHandle newObject, long mask, Class<?> modifiedClass) instead
            knowledgeHelper.update( updatedFact );
        }

        for ( FunctionN insert : consequence.getInserts() ) {
            Object insertedFact = insert.apply( facts );
            knowledgeHelper.insert( insertedFact );
        }

        for ( Variable delete : consequence.getDeletes() ) {
            Object deletedFact = context.getBoundFact( delete, objs );
            knowledgeHelper.delete( deletedFact );
        }
    }

    public static class DroolsImpl implements Drools {
        private final KnowledgeHelper knowledgeHelper;

        public DroolsImpl(KnowledgeHelper knowledgeHelper) {
            this.knowledgeHelper = knowledgeHelper;
        }

        @Override
        public void insert(Object object) {
            knowledgeHelper.insert(object);
        }

        @Override
        public void update(Object object) {
            knowledgeHelper.update(object);
        }

        @Override
        public void delete(Object object) {
            knowledgeHelper.delete(object);
        }
    }

}
