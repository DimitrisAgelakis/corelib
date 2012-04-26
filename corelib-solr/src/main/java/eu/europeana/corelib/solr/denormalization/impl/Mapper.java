package eu.europeana.corelib.solr.denormalization.impl;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.google.code.morphia.query.Query;

import eu.europeana.corelib.definitions.model.EdmLabel;
import eu.europeana.corelib.solr.denormalization.ControlledVocabulary;

/**
 * Mapper class for Controlled Vocabularies
 * 
 * @author yorgos.mamakis@ kb.nl
 * 
 */
public class Mapper {

	private VocabularyMongoServer mongoServer;

	private static ControlledVocabulary controlledVocabulary;

	public Mapper() {

	}

	public Mapper(ControlledVocabulary controlledVocabulary1,
			VocabularyMongoServer mongoServer) {
		controlledVocabulary = controlledVocabulary1;
		this.mongoServer = mongoServer;
	}

	/**
	 * Mapping function between a Europeana Field and an Element of the
	 * Controlled Vocabulary
	 * 
	 * @param field
	 *            The ControlledVocabulary Field
	 * @param europeanaField
	 *            The EuropeanaField
	 */
	public void mapField(String field, EdmLabel europeanaField) {
		controlledVocabulary.getElements().put(field, europeanaField);
	}

	/**
	 * Method saving the mapping of the controlled vocabulary
	 */
	public void saveMapping() {
		if (controlledVocabulary != null) {
			sanitize(controlledVocabulary);
			mongoServer.getDatastore().save(controlledVocabulary);
			System.out.println("Saved vocabulary");
		}
	}

	/**
	 * Method removing a vocabulary by name
	 * 
	 * @param vocabularyName
	 *            The vocabulary name to remove
	 */
	public void removeVocabulary(String vocabularyName) {
		Query<ControlledVocabularyImpl> query = mongoServer.getDatastore()
				.find(ControlledVocabularyImpl.class)
				.filter("name", vocabularyName);
		mongoServer.getDatastore().delete(query);
	}

	/**
	 * Sanitizing method that removes unmapped fields (the controlled vocabulary
	 * is initialized with full mappings)
	 * 
	 * @param controlledVocabulary
	 *            The controlled vocabulary to sanitize
	 */
	private void sanitize(ControlledVocabulary controlledVocabulary) {
		Set<Entry<String, EdmLabel>> elements = controlledVocabulary
				.getElements().entrySet();
		System.out.println(controlledVocabulary.getElements().size());
		Iterator<Entry<String, EdmLabel>> iterator = elements.iterator();
		while (iterator.hasNext()) {
			Entry<String, EdmLabel> entry = iterator.next();
			System.out.println(entry.getKey());

			if (StringUtils.equals(entry.getValue().toString(), "null")) {
				iterator.remove();
			}
		}
		System.out.println("out of sanitize while");
	}
}
