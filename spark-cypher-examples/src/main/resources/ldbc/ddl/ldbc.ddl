SET SCHEMA hive.LDBC

CREATE LABEL (City { name : STRING, url : STRING } )
CREATE LABEL (Comment { creationDate : STRING, locationIP : STRING, browserUsed : STRING, content : STRING, length : INTEGER } )
CREATE LABEL (Company { name : STRING, url : STRING } )
CREATE LABEL (Continent { name : STRING, url : STRING } )
CREATE LABEL (Country { name : STRING, url : STRING } )
CREATE LABEL (Forum { title : STRING, creationDate : STRING } )
CREATE LABEL (Person { firstName : STRING, lastName : STRING, gender : STRING, birthday : STRING, creationDate : STRING, locationIP : STRING, browserUsed : STRING } )
CREATE LABEL (Post { imageFile : STRING, creationDate : STRING, locationIP : STRING, browserUsed : STRING, language : STRING, content : STRING, length : INTEGER } )
CREATE LABEL (Tag { name : STRING, url : STRING } )
CREATE LABEL (Tagclass { name : STRING, url : STRING } )
CREATE LABEL (University { name : STRING, url : STRING } )
CREATE LABEL (containerof)
CREATE LABEL (hascreator)
CREATE LABEL (hasinterest)
CREATE LABEL (hasmember { joinDate : STRING } )
CREATE LABEL (hasmoderator)
CREATE LABEL (hastag)
CREATE LABEL (hastype)
CREATE LABEL (islocatedin)
CREATE LABEL (ispartof)
CREATE LABEL (issubclassof)
CREATE LABEL (knows { creationDate : STRING } )
CREATE LABEL (likes { creationDate : STRING } )
CREATE LABEL (replyof)
CREATE LABEL (studyat { classYear : INTEGER } )
CREATE LABEL (workat { workFrom : INTEGER } )

CREATE GRAPH SCHEMA LDBC_schema
    -- Node types
    (Forum),
	(Continent),
	(Person),
	(Comment),
	(Tagclass),
	(City),
	(Country),
	(University),
	(Post),
	(Company),
	(Tag)

    -- Edge types
    [hastype],
	[workat],
	[issubclassof],
	[islocatedin],
	[ispartof],
	[hastag],
	[hasinterest],
	[studyat],
	[hascreator],
	[knows],
	[hasmember],
	[containerof],
	[replyof],
	[hasmoderator],
	[likes]

    -- Edge constraints
    (Comment)-[islocatedin]->(Country),
	(Person)-[studyat]->(University),
	(Person)-[hasinterest]->(Tag),
	(City)-[ispartof]->(Country),
	(Post)-[hastag]->(Tag),
	(Company)-[islocatedin]->(Country),
	(Person)-[knows]->(Person),
	(Tag)-[hastype]->(Tagclass),
	(Forum)-[hasmoderator]->(Person),
	(Country)-[ispartof]->(Continent),
	(Comment)-[hascreator]->(Person),
	(Person)-[islocatedin]->(City),
	(Forum)-[hasmember]->(Person),
	(Post)-[islocatedin]->(Country),
	(Forum)-[containerof]->(Post),
	(Forum)-[hastag]->(Tag),
	(Person)-[workat]->(Company),
	(Comment)-[hastag]->(Tag),
	(Comment)-[replyof]->(Comment),
	(Comment)-[replyof]->(Post),
	(Post)-[hascreator]->(Person),
	(Tagclass)-[issubclassof]->(Tagclass),
	(University)-[islocatedin]->(City),
	(Person)-[likes]->(Comment),
	(Person)-[likes]->(Post)

CREATE GRAPH LDBC WITH GRAPH SCHEMA LDBC_schema
    NODE LABEL SETS (
        (Post) FROM post,
		(Tag) FROM tag,
		(Company) FROM company,
		(Tagclass) FROM tagclass,
		(Continent) FROM continent,
		(Person) FROM person,
		(Forum) FROM forum,
		(Comment) FROM comment,
		(University) FROM university,
		(Country) FROM country,
		(City) FROM city
    )

    RELATIONSHIP LABEL SETS (
        (hascreator) FROM post_hascreator_person edge START NODES LABEL SET (Post) FROM post node JOIN ON edge.Post.id = node.id END NODES LABEL SET (Person) FROM person node JOIN ON edge.Person.id = node.id,
		(hasmoderator) FROM forum_hasmoderator_person edge START NODES LABEL SET (Forum) FROM forum node JOIN ON edge.Forum.id = node.id END NODES LABEL SET (Person) FROM person node JOIN ON edge.Person.id = node.id,
		(issubclassof) FROM tagclass_issubclassof_tagclass edge START NODES LABEL SET (Tagclass) FROM tagclass node JOIN ON edge.Tagclass.id0 = node.id END NODES LABEL SET (Tagclass) FROM tagclass node JOIN ON edge.Tagclass.id1 = node.id,
		(islocatedin) FROM company_islocatedin_country edge START NODES LABEL SET (Company) FROM company node JOIN ON edge.Company.id = node.id END NODES LABEL SET (Country) FROM country node JOIN ON edge.Country.id = node.id,
		(hasinterest) FROM person_hasinterest_tag edge START NODES LABEL SET (Person) FROM person node JOIN ON edge.Person.id = node.id END NODES LABEL SET (Tag) FROM tag node JOIN ON edge.Tag.id = node.id,
		(workat) FROM person_workat_company edge START NODES LABEL SET (Person) FROM person node JOIN ON edge.Person.id = node.id END NODES LABEL SET (Company) FROM company node JOIN ON edge.Company.id = node.id,
		(replyof) FROM comment_replyof_comment edge START NODES LABEL SET (Comment) FROM comment node JOIN ON edge.Comment.id0 = node.id END NODES LABEL SET (Comment) FROM comment node JOIN ON edge.Comment.id1 = node.id,
		(islocatedin) FROM post_islocatedin_country edge START NODES LABEL SET (Post) FROM post node JOIN ON edge.Post.id = node.id END NODES LABEL SET (Country) FROM country node JOIN ON edge.Country.id = node.id,
		(ispartof) FROM country_ispartof_continent edge START NODES LABEL SET (Country) FROM country node JOIN ON edge.Country.id = node.id END NODES LABEL SET (Continent) FROM continent node JOIN ON edge.Continent.id = node.id,
		(hastag) FROM post_hastag_tag edge START NODES LABEL SET (Post) FROM post node JOIN ON edge.Post.id = node.id END NODES LABEL SET (Tag) FROM tag node JOIN ON edge.Tag.id = node.id,
		(islocatedin) FROM person_islocatedin_city edge START NODES LABEL SET (Person) FROM person node JOIN ON edge.Person.id = node.id END NODES LABEL SET (City) FROM city node JOIN ON edge.City.id = node.id,
		(islocatedin) FROM university_islocatedin_city edge START NODES LABEL SET (University) FROM university node JOIN ON edge.University.id = node.id END NODES LABEL SET (City) FROM city node JOIN ON edge.City.id = node.id,
		(knows) FROM person_knows_person edge START NODES LABEL SET (Person) FROM person node JOIN ON edge.Person.id0 = node.id END NODES LABEL SET (Person) FROM person node JOIN ON edge.Person.id1 = node.id,
		(studyat) FROM person_studyat_university edge START NODES LABEL SET (Person) FROM person node JOIN ON edge.Person.id = node.id END NODES LABEL SET (University) FROM university node JOIN ON edge.University.id = node.id,
		(likes) FROM person_likes_comment edge START NODES LABEL SET (Person) FROM person node JOIN ON edge.Person.id = node.id END NODES LABEL SET (Comment) FROM comment node JOIN ON edge.Comment.id = node.id,
		(hastag) FROM forum_hastag_tag edge START NODES LABEL SET (Forum) FROM forum node JOIN ON edge.Forum.id = node.id END NODES LABEL SET (Tag) FROM tag node JOIN ON edge.Tag.id = node.id,
		(containerof) FROM forum_containerof_post edge START NODES LABEL SET (Forum) FROM forum node JOIN ON edge.Forum.id = node.id END NODES LABEL SET (Post) FROM post node JOIN ON edge.Post.id = node.id,
		(islocatedin) FROM comment_islocatedin_country edge START NODES LABEL SET (Comment) FROM comment node JOIN ON edge.Comment.id = node.id END NODES LABEL SET (Country) FROM country node JOIN ON edge.Country.id = node.id,
		(hascreator) FROM comment_hascreator_person edge START NODES LABEL SET (Comment) FROM comment node JOIN ON edge.Comment.id = node.id END NODES LABEL SET (Person) FROM person node JOIN ON edge.Person.id = node.id,
		(hastag) FROM comment_hastag_tag edge START NODES LABEL SET (Comment) FROM comment node JOIN ON edge.Comment.id = node.id END NODES LABEL SET (Tag) FROM tag node JOIN ON edge.Tag.id = node.id,
		(likes) FROM person_likes_post edge START NODES LABEL SET (Person) FROM person node JOIN ON edge.Person.id = node.id END NODES LABEL SET (Post) FROM post node JOIN ON edge.Post.id = node.id,
		(ispartof) FROM city_ispartof_country edge START NODES LABEL SET (City) FROM city node JOIN ON edge.City.id = node.id END NODES LABEL SET (Country) FROM country node JOIN ON edge.Country.id = node.id,
		(replyof) FROM comment_replyof_post edge START NODES LABEL SET (Comment) FROM comment node JOIN ON edge.Comment.id = node.id END NODES LABEL SET (Post) FROM post node JOIN ON edge.Post.id = node.id,
		(hasmember) FROM forum_hasmember_person edge START NODES LABEL SET (Forum) FROM forum node JOIN ON edge.Forum.id = node.id END NODES LABEL SET (Person) FROM person node JOIN ON edge.Person.id = node.id,
		(hastype) FROM tag_hastype_tagclass edge START NODES LABEL SET (Tag) FROM tag node JOIN ON edge.Tag.id = node.id END NODES LABEL SET (Tagclass) FROM tagclass node JOIN ON edge.Tagclass.id = node.id
    )