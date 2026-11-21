package fr.walid.acd.contribution.adapters.importing;

import java.util.List;

/** One document rebuilt from the file, with the line it started on. */
record GroupedDocument(int line, java.util.Map<String, Object> document) {
}

/** What a file gave once regrouped: the documents it describes and the rows that cannot be one. */
record GroupedDocuments(List<GroupedDocument> documents, List<fr.walid.acd.contribution.application.importing.RowOutcome> rejections) {
}
