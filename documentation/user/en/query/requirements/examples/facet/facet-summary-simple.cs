EvitaResponse<ISealedEntity> entities = evita.QueryCatalog(
	"evita",
	session => session.QuerySealedEntity(
        Query(
        	Collection("Product"),
        	FilterBy(
        		HierarchyWithin(
        			"categories",
        			AttributeEquals("code", "e-readers")
        		),
        		AttributeEquals("status", "ACTIVE"),
        		EntityLocaleEquals(CultureInfo.GetCultureInfo("en"))
        	),
        	Require(
        		FacetSummary(
        			Counts,
        			EntityFetch(
        				AttributeContent("name")
        			),
        			EntityGroupFetch(
        				AttributeContent("name")
        			)
        		)
        	)
        )
	)
);