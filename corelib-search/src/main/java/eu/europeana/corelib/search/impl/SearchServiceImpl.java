/*
 * Copyright 2007-2012 The Europeana Foundation
 *
 *  Licenced under the EUPL, Version 1.1 (the "Licence") and subsequent versions as approved
 *  by the European Commission;
 *  You may not use this work except in compliance with the Licence.
 * 
 *  You may obtain a copy of the Licence at:
 *  http://joinup.ec.europa.eu/software/page/eupl
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the Licence is distributed on an "AS IS" basis, without warranties or conditions of
 *  any kind, either express or implied.
 *  See the Licence for the specific language governing permissions and limitations under
 *  the Licence.
 */
package eu.europeana.corelib.search.impl;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.protocol.HttpContext;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.ORDER;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.request.LukeRequest;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SpellCheckResponse;
import org.apache.solr.client.solrj.response.SpellCheckResponse.Collation;
import org.apache.solr.client.solrj.response.SpellCheckResponse.Correction;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;
import org.neo4j.graphdb.Node;
import org.springframework.beans.factory.annotation.Value;

import eu.europeana.corelib.definitions.edm.beans.BriefBean;
import eu.europeana.corelib.definitions.edm.beans.FullBean;
import eu.europeana.corelib.definitions.edm.beans.IdBean;
import eu.europeana.corelib.definitions.edm.entity.Proxy;
import eu.europeana.corelib.definitions.exception.ProblemType;
import eu.europeana.corelib.definitions.solr.model.Query;
import eu.europeana.corelib.definitions.solr.model.Term;
import eu.europeana.corelib.edm.exceptions.MongoDBException;
import eu.europeana.corelib.edm.exceptions.SolrTypeException;
import eu.europeana.corelib.edm.utils.SolrUtils;
import eu.europeana.corelib.logging.Log;
import eu.europeana.corelib.logging.Logger;
import eu.europeana.corelib.mongo.server.EdmMongoServer;
import eu.europeana.corelib.neo4j.entity.Neo4jBean;
import eu.europeana.corelib.neo4j.entity.Neo4jStructBean;
import eu.europeana.corelib.neo4j.entity.Node2Neo4jBeanConverter;
import eu.europeana.corelib.neo4j.server.Neo4jServer;
import eu.europeana.corelib.search.SearchService;
import eu.europeana.corelib.search.model.ResultSet;
import eu.europeana.corelib.search.query.MoreLikeThis;
import eu.europeana.corelib.search.utils.SearchUtils;
import eu.europeana.corelib.solr.bean.impl.ApiBeanImpl;
import eu.europeana.corelib.solr.bean.impl.BriefBeanImpl;
import eu.europeana.corelib.solr.bean.impl.IdBeanImpl;
import eu.europeana.corelib.solr.bean.impl.RichBeanImpl;
import eu.europeana.corelib.tools.lookuptable.EuropeanaId;
import eu.europeana.corelib.tools.lookuptable.EuropeanaIdMongoServer;
import eu.europeana.corelib.utils.EuropeanaUriUtils;

/**
 * @see eu.europeana.corelib.search.SearchService
 * 
 * @author Yorgos.Mamakis@ kb.nl
 * 
 */
public class SearchServiceImpl implements SearchService {

	/**
	 * Default number of documents retrieved by MoreLikeThis
	 */
	private static final int DEFAULT_MLT_COUNT = 10;

	private static boolean STARTED = false;
	private static final String UNION_FACETS_FORMAT = "'{'!ex={0}'}'{0}";

	/**
	 * Number of milliseconds before the query is aborted by SOLR
	 */
	private static final int TIME_ALLOWED = 30000;

	private Map<String, Long> total = new HashMap<String, Long>();

	/**
	 * The list of possible field input for spelling suggestions
	 */
	private static final List<String> SPELL_FIELDS = Arrays.asList(
		"who", "what", "where", "when", "title"
	);

	// provided by setter
	private SolrServer solrServer;

	@Resource(name = "corelib_solr_mongoServer")
	protected EdmMongoServer mongoServer;

	@Resource(name = "corelib_solr_idServer")
	protected EuropeanaIdMongoServer idServer;
        
        @Resource(name = "corelib_solr_neo4jServer")
        protected Neo4jServer neo4jServer;

	@Value("#{europeanaProperties['solr.facetLimit']}")
	private int facetLimit;

	@Value("#{europeanaProperties['solr.username']}")
	private String username;

	@Value("#{europeanaProperties['solr.password']}")
	private String password;

	private final static String RESOLVE_PREFIX = "http://www.europeana.eu/resolve/record";

	private final static String PORTAL_PREFIX = "http://www.europeana.eu/portal/record";

	private String mltFields;

	@Log
	protected Logger log;

	@Override
	public FullBean findById(String collectionId, String recordId, boolean similarItems)
			throws MongoDBException {
		return findById(EuropeanaUriUtils.createEuropeanaId(collectionId, recordId), similarItems);
	}

	@Override
	public FullBean findById(String europeanaObjectId, boolean similarItems) throws MongoDBException {
		long t0 = new Date().getTime();

		FullBean fullBean = mongoServer.getFullBean(europeanaObjectId);
		if(fullBean !=null && isHierarchy(fullBean.getAbout())){
			for(Proxy prx : fullBean.getProxies()){
				prx.setDctermsHasPart(null);
			}
		}
		logTime("mongo findById", (new Date().getTime() - t0));
		if (fullBean != null && similarItems) {
			try {
				fullBean.setSimilarItems(findMoreLikeThis(europeanaObjectId));
			} catch (SolrServerException e) {
				log.error("SolrServerException: " + e.getMessage());
			}
		}

		return fullBean;
	}

	@Override
	public FullBean resolve(String collectionId, String recordId, boolean similarItems)
			throws SolrTypeException {
		return resolve(EuropeanaUriUtils.createResolveEuropeanaId(collectionId,
				recordId), similarItems);
	}

	@Override
	public FullBean resolve(String europeanaObjectId, boolean similarItems) throws SolrTypeException {

		FullBean fullBean = resolveInternal(europeanaObjectId, similarItems);
		FullBean fullBeanNew = fullBean;
		if(fullBean!=null){
			while(fullBeanNew!=null){
				fullBeanNew = resolveInternal(fullBeanNew.getAbout(), similarItems);
				if(fullBeanNew!=null){
					fullBean = fullBeanNew;
				}
			}
		}

		return fullBean;
	}

	private FullBean resolveInternal(String europeanaObjectId, boolean similarItems) throws SolrTypeException{
		long t0 = new Date().getTime();
		if(!STARTED){
			idServer.createDatastore();
			STARTED=true;
		}
		mongoServer.setEuropeanaIdMongoServer(idServer);
		FullBean fullBean = mongoServer.resolve(europeanaObjectId);
		logTime("mongo resolve", (new Date().getTime() - t0));
		if (fullBean != null) {
			try {
				fullBean.setSimilarItems(findMoreLikeThis(fullBean.getAbout()));
			} catch (SolrServerException e) {
				log.error("SolrServerException: " + e.getMessage());
			}
		}
		return fullBean;
	}

	@Override
	public String resolveId(String europeanaObjectId) {
		String lastId = resolveIdInternal(europeanaObjectId);
		String newId = lastId;
		if(lastId!=null){
			while(newId!=null){
				newId = resolveIdInternal(newId);
				if(newId!=null){
					lastId = newId;
				}
			}
		}
		return lastId;
	}

	@Override
	public String resolveId(String collectionId, String recordId) {
		return resolveId(EuropeanaUriUtils.createResolveEuropeanaId(collectionId,
				recordId));
	}

	private String resolveIdInternal(String europeanaObjectId){
		if(!STARTED){
			idServer.createDatastore();
			STARTED= true;
		}
		EuropeanaId newId = idServer.retrieveEuropeanaIdFromOld(europeanaObjectId);
		if (newId != null) {
			idServer.updateTime(newId.getNewId(), europeanaObjectId);
			return newId.getNewId();
		}

		newId = idServer
				.retrieveEuropeanaIdFromOld(RESOLVE_PREFIX + europeanaObjectId);
		if (newId != null) {
			idServer.updateTime(newId.getNewId(), RESOLVE_PREFIX +europeanaObjectId);
			return newId.getNewId();
		}

		newId = idServer.retrieveEuropeanaIdFromOld(PORTAL_PREFIX+europeanaObjectId);

		if (newId != null) {
			idServer.updateTime(newId.getNewId(), PORTAL_PREFIX +europeanaObjectId);
			return newId.getNewId();
		}
		return null;
	}

	@Override
	public List<BriefBean> findMoreLikeThis(String europeanaObjectId)
			throws SolrServerException {
		return findMoreLikeThis(europeanaObjectId, DEFAULT_MLT_COUNT);
	}

	@Override
	public List<BriefBean> findMoreLikeThis(String europeanaObjectId, int count)
			throws SolrServerException {
		String query = "europeana_id:\"" + europeanaObjectId + "\"";

		SolrQuery solrQuery = new SolrQuery().setQuery(query);
		// solrQuery.setQueryType(QueryType.ADVANCED.toString());
		solrQuery.set("mlt", true);

		if (mltFields == null) {
			List<String> fields = new ArrayList<String>();
			for (MoreLikeThis mltField : MoreLikeThis.values()) {
				fields.add(mltField.toString());
			}
			mltFields = ClientUtils.escapeQueryChars(StringUtils.join(fields, ","));
		}
		solrQuery.set("mlt.fl", mltFields);
		solrQuery.set("mlt.mintf", 1);
		solrQuery.set("mlt.match.include", "false");
		solrQuery.set("mlt.count", count);
		solrQuery.set("rows", 1);
		solrQuery.setTimeAllowed(TIME_ALLOWED);
		if (log.isDebugEnabled()) {
			log.debug(solrQuery.toString());
		}

		QueryResponse response = solrServer.query(solrQuery);
		logTime("MoreLikeThis", response.getElapsedTime());

		@SuppressWarnings("unchecked")
		NamedList<Object> moreLikeThisList = (NamedList<Object>) response.getResponse().get("moreLikeThis");
		List<BriefBean> beans = new ArrayList<BriefBean>();
		if (moreLikeThisList.size() > 0) {
			@SuppressWarnings("unchecked")
			List<SolrDocument> docs = (List<SolrDocument>) moreLikeThisList.getVal(0);
			for (SolrDocument doc : docs) {
				beans.add(solrServer.getBinder().getBean(BriefBeanImpl.class, doc));
			}
		}
		return beans;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends IdBean> ResultSet<T> search(Class<T> beanInterface,
			Query query) throws SolrTypeException {

		ResultSet<T> resultSet = new ResultSet<T>();
		Class<? extends IdBeanImpl> beanClazz = SearchUtils.getImplementationClass(beanInterface);

		if (isValidBeanClass(beanClazz)) {
			String[] refinements = query.getRefinements(true);
			if (SearchUtils.checkTypeFacet(refinements)) {
				SolrQuery solrQuery = new SolrQuery().setQuery(query.getQuery(true));

				if (refinements != null) {
					solrQuery.addFilterQuery(refinements);
				}

				solrQuery.setRows(query.getPageSize());
				solrQuery.setStart(query.getStart());

				// These are going to change when we import ASSETS as well
				// solrQuery.setQueryType(QueryType.ADVANCED.toString());
				// query.setQueryType(solrQuery.getQueryType());

				// solrQuery.setSortField("COMPLETENESS", ORDER.desc);
				solrQuery.setSortField("score", ORDER.desc);
				solrQuery.setTimeAllowed(TIME_ALLOWED);
				// add extra parameters if any
				if (query.getParameters() != null) {
					Map<String, String> parameters = query.getParameters();
					for (String key : parameters.keySet()) {
						solrQuery.setParam(key, parameters.get(key));
					}
				}

				// facets are optional
				if (query.isAllowFacets()) {
					solrQuery.setFacet(true);
					List<String> filteredFacets = query.getFilteredFacets();
					boolean hasFacetRefinements = (filteredFacets != null && filteredFacets.size() > 0);
					for (String facetToAdd : query.getFacets()) {
						if (query.isProduceFacetUnion()) {
							if (hasFacetRefinements && filteredFacets.contains(facetToAdd)) {
								facetToAdd = MessageFormat.format(UNION_FACETS_FORMAT, facetToAdd);
							}
						}
						solrQuery.addFacetField(facetToAdd);
					}
					solrQuery.setFacetLimit(facetLimit);
				}

				// spellcheck is optional
				if (query.isAllowSpellcheck()) {
					if (solrQuery.getStart() == null
							|| solrQuery.getStart().intValue() <= 1) {
						solrQuery.setParam("spellcheck", "on");
						solrQuery.setParam("spellcheck.collate", "true");
						solrQuery.setParam("spellcheck.extendedResults", "true");
						solrQuery.setParam("spellcheck.onlyMorePopular", "true");
						solrQuery.setParam("spellcheck.q", query.getQuery());
					}
				}

				if (query.getFacetQueries() != null) {
					for (String facetQuery : query.getFacetQueries()) {
						solrQuery.addFacetQuery(facetQuery);
					}
				}

				try {
					if (log.isDebugEnabled()) {
						log.debug("Solr query is: " + solrQuery);
					}
					query.setExecutedQuery(solrQuery.toString());
					QueryResponse queryResponse = solrServer.query(solrQuery);
					logTime("search", queryResponse.getElapsedTime());

					resultSet.setResults((List<T>) queryResponse.getBeans(beanClazz));
					resultSet.setFacetFields(queryResponse.getFacetFields());
					resultSet.setResultSize(queryResponse.getResults().getNumFound());
					resultSet.setSearchTime(queryResponse.getElapsedTime());
					resultSet.setSpellcheck(queryResponse.getSpellCheckResponse());
					if (queryResponse.getFacetQuery() != null) {
						resultSet.setQueryFacets(queryResponse.getFacetQuery());
					}
				} catch (SolrServerException e) {
					log.error("SolrServerException: " + e.getMessage() + " The query was: " + solrQuery);
					resultSet = null;
					throw new SolrTypeException(e, ProblemType.MALFORMED_QUERY);
				} catch (SolrException e) {
					log.error("SolrException: " + e.getMessage() + " The query was: " + solrQuery);
					resultSet = null;
					throw new SolrTypeException(e, ProblemType.MALFORMED_QUERY);
				}

			} else {
				resultSet = null;
				throw new SolrTypeException(ProblemType.INVALIDARGUMENTS);
			}

		} else {
			resultSet = null;
			ProblemType type = ProblemType.INVALIDCLASS;
			type.appendMessage("Bean class: " + beanClazz);
			throw new SolrTypeException(type);
		}
		return resultSet;
	}

	/**
	 * Flag whether the bean class is one of the allowable ones.
	 * @param beanClazz
	 * @return
	 */
	private boolean isValidBeanClass(Class<? extends IdBeanImpl> beanClazz) {
		return beanClazz == BriefBeanImpl.class
			|| beanClazz == ApiBeanImpl.class
			|| beanClazz == RichBeanImpl.class;
	}

	@Override
	public List<Term> suggestions(String query, int pageSize)
			throws SolrTypeException {
		return suggestions(query, pageSize, null);
	}

	@Override
	public List<Count> createCollections(String facetFieldName,
			String queryString, String... refinements)
				throws SolrTypeException {

		Query query = new Query(queryString)
				.setParameter("rows", "0")
				.setParameter("facet", "true")
				.setRefinements(refinements)
				.setParameter("facet.mincount", "1")
				.setParameter("facet.limit", "750")
				.setAllowSpellcheck(false);
		query.setFacet(facetFieldName);

		final ResultSet<BriefBean> response = search(BriefBean.class, query);
		for (FacetField facetField : response.getFacetFields()) {
			if (facetField.getName().equalsIgnoreCase(facetFieldName)) {
				return facetField.getValues();
			}
		}
		return new ArrayList<FacetField.Count>();
	}

	@Override
	public Map<String, Integer> seeAlso(List<String> queries) {
		return queryFacetSearch("*:*", null, queries);
	}

	@Override
	public Map<String, Integer> queryFacetSearch(String query, String[] qf, List<String> queries) {
		SolrQuery solrQuery = new SolrQuery();
		solrQuery.setQuery(query);
		if (qf != null) {
			solrQuery.addFilterQuery(qf);
		}
		solrQuery.setRows(0);
		solrQuery.setFacet(true);
		solrQuery.setTimeAllowed(TIME_ALLOWED);
		for (String queryFacet : queries) {
			solrQuery.addFacetQuery(queryFacet);
		}
		QueryResponse response;
		Map<String, Integer> queryFacets = null;
		try {
			if (log.isDebugEnabled()) {
				log.debug("Solr query is: " + solrQuery.toString());
			}
			response = solrServer.query(solrQuery);
			logTime("queryFacetSearch", response.getElapsedTime());
			queryFacets = response.getFacetQuery();
		} catch (SolrServerException e) {
			log.error("SolrServerException: " + e.getMessage() + " for query " + solrQuery.toString());
			e.printStackTrace();
		} catch (Exception e) {
			log.error("Exception: " + e.getClass().getCanonicalName() + " " + e.getMessage() + " for query " 
					+ solrQuery.toString());
			e.printStackTrace();
		}

		return queryFacets;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends IdBean> ResultSet<T> sitemap(Class<T> beanInterface, Query query) throws SolrTypeException {

		ResultSet<T> resultSet = new ResultSet<T>();
		Class<? extends IdBeanImpl> beanClazz = SearchUtils.getImplementationClass(beanInterface);

		String[] refinements = query.getRefinements(true);
		if (SearchUtils.checkTypeFacet(refinements)) {
			SolrQuery solrQuery = new SolrQuery().setQuery(query.getQuery());

			if (refinements != null) {
				solrQuery.addFilterQuery(refinements);
			}

			solrQuery.setFacet(false);
			solrQuery.setRows(query.getPageSize());
			solrQuery.setStart(query.getStart());

			solrQuery.setSortField("COMPLETENESS", ORDER.desc);
			solrQuery.setSortField("score", ORDER.desc);
			solrQuery.setTimeAllowed(TIME_ALLOWED);
			// add extra parameters if any
			if (query.getParameters() != null) {
				Map<String, String> parameters = query.getParameters();
				for (String key : parameters.keySet()) {
					solrQuery.setParam(key, parameters.get(key));
				}
			}

			try {
				if (log.isDebugEnabled()) {
					log.debug("Solr query is: " + solrQuery);
				}
				QueryResponse queryResponse = solrServer.query(solrQuery);
				logTime("search", queryResponse.getElapsedTime());

				resultSet.setResults((List<T>) queryResponse.getBeans(beanClazz));
				resultSet.setResultSize(queryResponse.getResults().getNumFound());
				resultSet.setSearchTime(queryResponse.getElapsedTime());
				if (solrQuery.getBool("facet", false)) {
					resultSet.setFacetFields(queryResponse.getFacetFields());
				}
			} catch (SolrServerException e) {
				log.error("SolrServerException: " + e.getMessage());
				throw new SolrTypeException(e, ProblemType.MALFORMED_QUERY);
			} catch (SolrException e) {
				log.error("SolrException: " + e.getMessage());
				throw new SolrTypeException(e, ProblemType.MALFORMED_QUERY);
			}
		}

		return resultSet;
	}

	/**
	 * Get Suggestions from Solr Suggester
	 * 
	 * @param query
	 *            The query term
	 * @param field
	 *            The field to query on
	 * @param rHandler
	 *            The ReqestHandler to use
	 * @return A list of Terms for the specific term from the SolrSuggester
	 */
	private List<Term> getSuggestions(String query, String field, String rHandler) {
		List<Term> results = new ArrayList<Term>();
		try {
			ModifiableSolrParams params = new ModifiableSolrParams();
			params.set("qt", "/" + rHandler);
			params.set("q", field + ":" + query);
			params.set("rows", 0);
			params.set("timeAllowed", TIME_ALLOWED);

			// get the query response
			QueryResponse queryResponse = solrServer.query(params);
			SpellCheckResponse spellcheckResponse = queryResponse.getSpellCheckResponse();
			//if the suggestions are not empty and there are collated results
			if (spellcheckResponse != null
				&& !spellcheckResponse.getSuggestions().isEmpty()
				&& spellcheckResponse.getCollatedResults() != null) {
				for (Collation collation : spellcheckResponse.getCollatedResults()) {
					StringBuilder termResult = new StringBuilder();
					for (Correction cor : collation.getMisspellingsAndCorrections()) {
						//pickup the corrections, remove duplicates
						String[] terms = cor.getCorrection().trim().replaceAll("  ", " ").split(" ");
						for (String term : terms) {
							if (StringUtils.isBlank(term)) {
								continue;
							}
							// termResult.
							if (!StringUtils.contains(termResult.toString(), term)) {
								termResult.append(term + " ");
							}
						}
					}
					//return the term, the number of hits for each collation and the field that it should be mapped to
					Term term = new Term(termResult.toString().trim(),
							collation.getNumberOfHits(),
							SuggestionTitle.getMappedTitle(field),
							SearchUtils.escapeFacet(field, termResult.toString()));
					results.add(term);
				}
			}
		} catch (SolrServerException e) {
			log.error("Exception :" + e.getMessage());
		}

		return results;
	}

	/**
	 * Get the suggestions 
	 */
	@Override
	public List<Term> suggestions(String query, int pageSize, String field) {
		if (log.isDebugEnabled()) {
			log.debug(String.format("%s, %d, %s", query, pageSize, field));
		}
		List<Term> results = new ArrayList<Term>();
		long start = new Date().getTime();
		total.put(query, 0l);
		// if the fiels is null check on all fields else on the requested field
		if (StringUtils.isBlank(field) || !SPELL_FIELDS.contains(field)) {
			results.addAll(getSuggestions(query, "title", "suggestTitle"));
			results.addAll(getSuggestions(query, "who", "suggestWho"));
			results.addAll(getSuggestions(query, "what", "suggestWhat"));
			results.addAll(getSuggestions(query, "where", "suggestWhere"));
			results.addAll(getSuggestions(query, "when", "suggestWhen"));
		} else if (StringUtils.equals(field, SuggestionTitle.TITLE.title)) {
			results.addAll(getSuggestions(query, field, "suggestTitle"));
		} else if (StringUtils.equals(field, SuggestionTitle.PERSON.title)) {
			results.addAll(getSuggestions(query, field, "suggestWho"));
		} else if (StringUtils.equals(field, SuggestionTitle.SUBJECT.title)) {
			results.addAll(getSuggestions(query, field, "suggestWhat"));
		} else if (StringUtils.equals(field, SuggestionTitle.PLACE.title)) {
			results.addAll(getSuggestions(query, field, "suggestWhere"));
		} else if (StringUtils.equals(field, SuggestionTitle.DATE.title)) {
			results.addAll(getSuggestions(query, field, "suggestWhen"));
		}

		// Sort the results by number of hits
		Collections.sort(results);
		logTime("suggestions", (new Date().getTime() - start));
		total.remove(query);

		if (log.isDebugEnabled()) {
			log.debug(String.format("Returned %d results in %d ms",
				results.size() > pageSize ? pageSize : results.size(),
				new Date().getTime() - start));
		}
		return results.size() > pageSize ? results.subList(0, pageSize)
				: results;
	}

	public void setSolrServer(SolrServer solrServer) {
		this.solrServer = setServer(solrServer);
	}

	private SolrServer setServer(SolrServer solrServer) {
		if (solrServer instanceof HttpSolrServer) {
			HttpSolrServer server = new HttpSolrServer(
					((HttpSolrServer) solrServer).getBaseURL());
			AbstractHttpClient client = (AbstractHttpClient) server.getHttpClient();
			client.addRequestInterceptor(new PreEmptiveBasicAuthenticator(
					username, password));
			return server;
		} else {
			return solrServer;
		}
	}

	@Override
	public List<Neo4jBean> getChildren(String nodeId, int offset, int limit) {
		List<Node> children = neo4jServer.getChildren(getNode(nodeId), offset, limit);
		List<Neo4jBean> beans = new ArrayList<Neo4jBean>();
		for (Node child : children) {
			beans.add(Node2Neo4jBeanConverter.toNeo4jBean(child, getNodeId(child)));
		}
		return beans;
	}

	@Override
	public boolean isHierarchy (String nodeId){
		return neo4jServer.isHierarchy(nodeId);
	}
	@Override
	public List<Neo4jBean> getChildren(String nodeId, int offset) {
		return getChildren(nodeId, offset, 10);
	}

	@Override
	public List<Neo4jBean> getChildren(String nodeId) {
		return getChildren(nodeId, 0, 10);
	}

	private Node getNode(String id) {
		return neo4jServer.getNode(id);
	}

	@Override
	public Neo4jBean getHierarchicalBean(String nodeId) {
		Node node = getNode(nodeId);
		if (node != null) {
			return Node2Neo4jBeanConverter.toNeo4jBean(node, getNodeId(node));
		}
		return null;
	}

	private enum SuggestionTitle {
		TITLE("title", "Title"),
		DATE("when", "Time/Period"),
		PLACE("where", "Place"),
		PERSON("who", "Creator"),
		SUBJECT("what", "Subject");

		String title;
		String mappedTitle;

		private SuggestionTitle(String title, String mappedTitle) {
			this.title = title;
			this.mappedTitle = mappedTitle;
		}

		public static String getMappedTitle(String title) {
			for (SuggestionTitle st : SuggestionTitle.values()) {
				if (StringUtils.equals(title, st.title)) {
					return st.mappedTitle;
				}
			}
			return null;
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public Date getLastSolrUpdate() throws SolrServerException, IOException {
		long t0 = new Date().getTime();
		NamedList<Object> namedList = solrServer.request(new LukeRequest());
		NamedList<Object> index = (NamedList<Object>) namedList.get("index");
		if (log.isInfoEnabled()) {
			log.info("spent: " + (new Date().getTime() - t0));
		}
		return (Date) index.get("lastModified");
	}

	public void logTime(String type, long time) {
		if (log.isDebugEnabled()) {
			log.debug(String.format("elapsed time (%s): %d", type, time));
		}
	}

	

	@Override
	public List<Neo4jBean> getPreceedingSiblings(String nodeId, int limit) {
		List<Node> children = neo4jServer.getPreceedingSiblings(getNode(nodeId), limit);
		List<Neo4jBean> beans = new ArrayList<Neo4jBean>();
		for (Node child : children) {
			beans.add(Node2Neo4jBeanConverter.toNeo4jBean(child, getNodeId(child)));
		}
		return beans;
	}

	@Override
	public List<Neo4jBean> getPreceedingSiblings(String nodeId) {
		return getPreceedingSiblings(nodeId, 10);
	}

	@Override
	public List<Neo4jBean> getFollowingSiblings(String nodeId, int limit) {
		List<Node> children = neo4jServer.getFollowingSiblings(getNode(nodeId), limit);
		List<Neo4jBean> beans = new ArrayList<Neo4jBean>();
		for (Node child : children) {
			beans.add(Node2Neo4jBeanConverter.toNeo4jBean(child, getNodeId(child)));
		}
		return beans;
	}

	@Override
	public List<Neo4jBean> getFollowingSiblings(String nodeId) {
		return getFollowingSiblings(nodeId, 10);
	}

	@Override
	public long getChildrenCount(String nodeId) {
		return neo4jServer.getChildrenCount(getNode(nodeId));
	}

	private long getNodeId(Node nodeId){
		return neo4jServer.getNodeIndex(nodeId);
	}

	@Override
	public Neo4jStructBean getInitialStruct(String nodeId) {
		return Node2Neo4jBeanConverter.toNeo4jStruct(neo4jServer.getInitialStruct(nodeId));
	}
}

class PreEmptiveBasicAuthenticator implements HttpRequestInterceptor {
	private final UsernamePasswordCredentials credentials;

	public PreEmptiveBasicAuthenticator(String user, String pass) {
		credentials = new UsernamePasswordCredentials(user, pass);
	}

	@Override
	public void process(HttpRequest request, HttpContext context)
			throws HttpException, IOException {
		request.addHeader(BasicScheme.authenticate(credentials, "US-ASCII", false));
	}
}
