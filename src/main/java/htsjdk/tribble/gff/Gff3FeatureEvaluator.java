package htsjdk.tribble.gff;

import com.sun.tools.javac.util.Pair;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.tribble.TribbleException;
import org.obolibrary.obo2owl.OWLAPIObo2Owl;
import org.obolibrary.oboformat.parser.OBOFormatConstants;

import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.util.OWLOntologyMerger;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Gff3FeatureEvaluator {
    private OWLOntology ontology;
    private OWLReasoner reasoner;
    final private OWLOntologyManager manager;

    final private Map<String, OWLClassExpression> humanReadableToOWLClassMap = new LinkedHashMap<>();

    final private Map<Pair<String, String>, Boolean> isSubClassOfCache = new HashMap<>();
    final private Map<Pair<String, String>, Boolean> isPartOfCache = new HashMap<>();

    final static private OWLDataFactory df = OWLManager.getOWLDataFactory();

    final static URL DEFAULT_ONTOLOGY_URL = Gff3FeatureEvaluator.class.getResource("so.obo");

    final static private OWLAnnotationProperty idAnnotationProperty = df.getOWLAnnotationProperty(OWLAPIObo2Owl.trTagToIRI(OBOFormatConstants.OboFormatTag.TAG_ID.getTag()));
    final static private OWLAnnotationProperty labelAnnotationProperty = df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI());
    final private List<URL> addedOntologies = new ArrayList<>();
    final static private OWLObjectPropertyExpression partOfProperty = df.getOWLObjectProperty("http://purl.obolibrary.org/obo/so#part_of");

    public Gff3FeatureEvaluator() {
        manager = OWLManager.createOWLOntologyManager();
        try {
            ontology = manager.loadOntology(IRI.create(DEFAULT_ONTOLOGY_URL));
            final ReasonerFactory reasonerFactory = new ReasonerFactory();
            reasoner = reasonerFactory.createReasoner(ontology);

            //load human readable names map
            ontology.classesInSignature().forEach(this::loadHumanReadableNames);
        } catch (final OWLOntologyCreationException ex) {
            throw new TribbleException("error loading ontology from " + DEFAULT_ONTOLOGY_URL);
        }
    }

    public void addOntology(final URL ontologyURL) {
        try {
            manager.loadOntology(IRI.create(ontologyURL));
            final OWLOntologyMerger merger = new OWLOntologyMerger(manager);

            final OWLOntologyManager mergingManager = OWLManager.createOWLOntologyManager();
            ontology = merger.createMergedOntology(manager, null);
            humanReadableToOWLClassMap.clear();
            final ReasonerFactory reasonerFactory = new ReasonerFactory();
            reasoner = reasonerFactory.createReasoner(ontology);

            //load human readable names map
            ontology.classesInSignature().forEach(this::loadHumanReadableNames);
            addedOntologies.add(ontologyURL);
        } catch (final OWLOntologyCreationException ex) {
            throw new TribbleException("error adding ontology from " + ontologyURL);
        }
    }

    public boolean isASubClassOf(final String type, final String superClassType) {
        return isSubClassOfCache.computeIfAbsent(Pair.of(type, superClassType), p -> decideIfSubClassOf(p.fst, p.snd));
    }

    private boolean decideIfSubClassOf(final String type, final String superClassType) {
        final OWLClassExpression cls = humanReadableToOWLClassMap.get(type);
        final OWLClassExpression superCls = humanReadableToOWLClassMap.get(superClassType);
        if (cls == null) {
            throw new TribbleException("type " + type + " used in GFF3 file not found in ontology.");
        }
        if(superCls == null) {
            throw new TribbleException("type " + superClassType + " used in GFF3 file not found in ontology.");
        }
        final OWLAxiom isSubClassOfAxiom = df.getOWLSubClassOfAxiom(cls, superCls);

        return reasoner.isEntailed(isSubClassOfAxiom);
    }

    public boolean isPartOf(final String type1, final String type2) {
        return isPartOfCache.computeIfAbsent(Pair.of(type1, type2), p -> decideIfPartOf(p.fst, p.snd));
    }

    private boolean decideIfPartOf(final String type1, final String type2) {
        //does type1 have a "part_of" relationship with type2?
        final OWLClassExpression cls1 = humanReadableToOWLClassMap.get(type1);
        final OWLClassExpression cls2 = humanReadableToOWLClassMap.get(type2);

        OWLClassExpression cls = df.getOWLObjectSomeValuesFrom(partOfProperty, cls2);

        final OWLAxiom isSubClassOfAxiom = df.getOWLSubClassOfAxiom(cls1, cls);
        df.getOWLO
        return reasoner.isEntailed(isSubClassOfAxiom);
    }

    public boolean isValidType(final String type) {
        return humanReadableToOWLClassMap.containsKey(type);
    }

    public List<URL> getAddedOntologies() {
        return addedOntologies;
    }

    void loadHumanReadableNames(final OWLClass ce) {
            if (EntitySearcher.getAnnotationAssertionAxioms(ce, ontology).noneMatch(OWLAnnotationAssertionAxiom::isDeprecatedIRIAssertion)) {
                addIdentifierToMap(ce, idAnnotationProperty);
                addIdentifierToMap(ce, labelAnnotationProperty);
            }
    }

    void addIdentifierToMap(final OWLClass ce, final OWLAnnotationProperty annotationProperty) {
        EntitySearcher.getAnnotationObjects(ce, ontology, annotationProperty).forEach(an -> {
                    final String identifier = ((OWLLiteral)an.getValue()).getLiteral();
                    checkAndAddToMap(identifier, ce);
                }
        );
    }

    void checkAndAddToMap(final String identifier, final OWLClass ce) {
        if (identifier != null) {
            if (humanReadableToOWLClassMap.containsKey(identifier)) {
                throw new TribbleException("Human readable identifier " + identifier + " found associated with multiple classes when loading GFF3 ontology.");
            }
            humanReadableToOWLClassMap.put(identifier, ce);
        }
    }


}
