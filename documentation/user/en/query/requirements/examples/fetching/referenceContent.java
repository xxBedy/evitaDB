final EvitaResponse<SealedEntity> entities = evita.queryCatalog(
	"evita",
	session -> {
		return session.querySealedEntity(
			query(
				collection("Product"),
				filterBy(
					entityPrimaryKeyInSet(103885)
				),
				require(
					entityFetch(
						attributeContent("code"),
						referenceContent("brand"),
						referenceContent("categories")
					)
				)
			)
		);
	}
);