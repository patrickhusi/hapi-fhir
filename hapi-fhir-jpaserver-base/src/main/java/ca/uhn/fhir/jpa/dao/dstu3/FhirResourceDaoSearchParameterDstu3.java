package ca.uhn.fhir.jpa.dao.dstu3;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.dao.BaseSearchParamExtractor;
import ca.uhn.fhir.jpa.dao.IFhirResourceDaoSearchParameter;
import ca.uhn.fhir.jpa.dao.IFhirSystemDao;
import ca.uhn.fhir.jpa.dao.ISearchParamRegistry;
import ca.uhn.fhir.jpa.dao.r4.FhirResourceDaoSearchParameterR4;
import ca.uhn.fhir.jpa.entity.ResourceTable;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import ca.uhn.fhir.util.ElementUtil;
import org.apache.commons.lang3.time.DateUtils;
import org.hl7.fhir.dstu3.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;

/*
 * #%L
 * HAPI FHIR JPA Server
 * %%
 * Copyright (C) 2014 - 2018 University Health Network
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

public class FhirResourceDaoSearchParameterDstu3 extends FhirResourceDaoDstu3<SearchParameter> implements IFhirResourceDaoSearchParameter<SearchParameter> {

	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(FhirResourceDaoSearchParameterDstu3.class);

	@Autowired
	private ISearchParamRegistry mySearchParamRegistry;

	@Autowired
	private IFhirSystemDao<Bundle, Meta> mySystemDao;

	protected void markAffectedResources(SearchParameter theResource) {
		Boolean reindex = theResource != null ? CURRENTLY_REINDEXING.get(theResource) : null;
		String expression = theResource != null ? theResource.getExpression() : null;
		markResourcesMatchingExpressionAsNeedingReindexing(reindex, expression);
	}

	/**
	 * This method is called once per minute to perform any required re-indexing. During most passes this will
	 * just check and find that there are no resources requiring re-indexing. In that case the method just returns
	 * immediately. If the search finds that some resources require reindexing, the system will do multiple
	 * reindexing passes and then return.
	 */
	@Override
	@Scheduled(fixedDelay = DateUtils.MILLIS_PER_MINUTE)
	@Transactional(propagation = Propagation.NEVER)
	public void performReindexingPass() {
		if (getConfig().isSchedulingDisabled()) {
			return;
		}

		Integer count = mySystemDao.performReindexingPass(100);
		for (int i = 0; i < 50 && count != null && count != 0; i++) {
			count = mySystemDao.performReindexingPass(100);
			try {
				Thread.sleep(DateUtils.MILLIS_PER_SECOND);
			} catch (InterruptedException e) {
				break;
			}
		}

	}

	@Override
	protected void postPersist(ResourceTable theEntity, SearchParameter theResource) {
		super.postPersist(theEntity, theResource);
		markAffectedResources(theResource);
	}

	@Override
	protected void postUpdate(ResourceTable theEntity, SearchParameter theResource) {
		super.postUpdate(theEntity, theResource);
		markAffectedResources(theResource);
	}

	@Override
	protected void preDelete(SearchParameter theResourceToDelete, ResourceTable theEntityToDelete) {
		super.preDelete(theResourceToDelete, theEntityToDelete);
		markAffectedResources(theResourceToDelete);
	}

	@Override
	protected void validateResourceForStorage(SearchParameter theResource, ResourceTable theEntityToSave) {
		super.validateResourceForStorage(theResource, theEntityToSave);

		Enum<?> status = theResource.getStatus();
		List<CodeType> base = theResource.getBase();
		String expression = theResource.getExpression();
		FhirContext context = getContext();
		Enumerations.SearchParamType type = theResource.getType();

		FhirResourceDaoSearchParameterR4.validateSearchParam(type, status, base, expression, context);
	}

}
