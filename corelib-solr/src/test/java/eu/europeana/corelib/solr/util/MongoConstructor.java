package eu.europeana.corelib.solr.util;

import java.util.ArrayList;
import java.util.List;

import eu.europeana.corelib.definitions.jibx.RDF;
import eu.europeana.corelib.definitions.jibx.RDF.Choice;
import eu.europeana.corelib.solr.bean.impl.FullBeanImpl;
import eu.europeana.corelib.solr.entity.AgentImpl;
import eu.europeana.corelib.solr.entity.AggregationImpl;
import eu.europeana.corelib.solr.entity.ConceptImpl;
import eu.europeana.corelib.solr.entity.PlaceImpl;
import eu.europeana.corelib.solr.entity.ProvidedCHOImpl;
import eu.europeana.corelib.solr.entity.ProxyImpl;
import eu.europeana.corelib.solr.entity.TimespanImpl;
import eu.europeana.corelib.solr.entity.WebResourceImpl;
import eu.europeana.corelib.solr.server.MongoDBServer;
import eu.europeana.corelib.solr.server.importer.util.AgentFieldInput;
import eu.europeana.corelib.solr.server.importer.util.AggregationFieldInput;
import eu.europeana.corelib.solr.server.importer.util.ConceptFieldInput;
import eu.europeana.corelib.solr.server.importer.util.PlaceFieldInput;
import eu.europeana.corelib.solr.server.importer.util.ProvidedCHOFieldInput;
import eu.europeana.corelib.solr.server.importer.util.ProxyFieldInput;
import eu.europeana.corelib.solr.server.importer.util.TimespanFieldInput;
import eu.europeana.corelib.solr.server.importer.util.WebResourcesFieldInput;

public class MongoConstructor {
	private static RDF record;
	private static FullBeanImpl fullBean;
	private static MongoDBServer mongoServer;



	public static void setParameters( MongoDBServer mongoServer){
		
		MongoConstructor.mongoServer = mongoServer;
	}
	
	public static RDF getRecord() {
		return MongoConstructor.record;
	}

	public static void constructFullBean(RDF record) throws InstantiationException, IllegalAccessException {
		fullBean = new FullBeanImpl();
		MongoConstructor.record = record;
		List<AgentImpl> agents = new ArrayList<AgentImpl>();
		List<AggregationImpl> aggregations = new ArrayList<AggregationImpl>();
		List<ConceptImpl> concepts = new ArrayList<ConceptImpl>();
		List<PlaceImpl> places = new ArrayList<PlaceImpl>();
		List<WebResourceImpl> webResources = new ArrayList<WebResourceImpl>();
		List<TimespanImpl> timespans = new ArrayList<TimespanImpl>();
		List<ProxyImpl> proxies = new ArrayList<ProxyImpl>();
		List<ProvidedCHOImpl> providedCHOs = new ArrayList<ProvidedCHOImpl>();
		List<Choice> elements = record.getChoiceList();
		for (Choice element : elements) {

			if (element.ifProvidedCHO()) {
				try {
					providedCHOs.add(ProvidedCHOFieldInput
							.createProvidedCHOMongoFields(element.getProvidedCHO(),
									mongoServer, false));
					if(proxies.size()>0){
						proxies.set(0, ProxyFieldInput.createProxyMongoFields(new ProxyImpl(),element.getProvidedCHO(), mongoServer));
					}
					else{
						proxies.add(ProxyFieldInput.createProxyMongoFields(new ProxyImpl(),element.getProvidedCHO(), mongoServer));
					}
				} catch (InstantiationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			if (element.ifAggregation()) {
				aggregations.add(AggregationFieldInput.createAggregationMongoFields(element.getAggregation(), mongoServer));
				if(webResources.size()>0){
					aggregations.set(0,AggregationFieldInput.appendWebResource(aggregations,webResources, mongoServer));
				}
				if(proxies.size()>0){
					proxies.set(0,ProxyFieldInput.addProxyForMongo(proxies.get(0),element.getAggregation(),mongoServer));
				}
				else{
					proxies.add(ProxyFieldInput.addProxyForMongo(new ProxyImpl(),element.getAggregation(),mongoServer));
				}
				
			}
			if (element.ifConcept()) {
				concepts.add(ConceptFieldInput.createConceptMongoFields(
						element.getConcept(), mongoServer));
			}
			if (element.ifPlace()) {
				places.add(PlaceFieldInput.createPlaceMongoFields(
						element.getPlace(), mongoServer));
			}
			
			if (element.ifWebResource()) {
				webResources.add(WebResourcesFieldInput.createWebResourceMongoField(element.getWebResource(), mongoServer));
				if(aggregations.size()>0){
					aggregations.set(0, AggregationFieldInput.appendWebResource(aggregations, webResources, mongoServer));
				}
				
			}
			if (element.ifTimeSpan()) {
				timespans
						.add(TimespanFieldInput.createTimespanMongoField(element.getTimeSpan(),mongoServer));
			}
			if (element.ifAgent()) {
				agents.add(AgentFieldInput.createAgentMongoEntity(element.getAgent(),mongoServer));
			}
		}

		AggregationImpl aggregation = aggregations.get(0);
		aggregation.setWebResources(webResources);
		fullBean.setProvidedCHOs(providedCHOs);
		fullBean.setAgents(agents);

		fullBean.setAggregations(aggregations);
		try{
		fullBean.setConcepts(concepts);
		fullBean.setPlaces(places);
		fullBean.setTimespans(timespans);
		fullBean.setProxies(proxies);}
		catch(Exception e){
			e.printStackTrace();
		}
		
		MongoConstructor.record = null;
		
		fullBean = null;
	}


}