# NoSQLite
A single file NoSQL database utilizing SQLite JSON1 extension.
It's a server-less embedded document database, ideal for small web applications.

**It features:**
- Embedded key-value object store
- Single file store
- Very fast and lightweight MongoDB like API
- Full text search capability
- Observable store

```java
import static nosqlite.Database.collection;

User john = new User("John");
collection("User").save(john);  // create or update user

List<User> users = collection("User").find();  // get all users
```

## Table of content
- [Getting started]()
- [CollectionOptions](#collectionoptions)
- [Import](#import)
- [Export](#export)
- [Drop](#drop)
- [document](#document)
- [Collection methods](#collection-methods)
  - [Filters](#filters)
  - [FindOptions](#findoptions)
- [Collection Examples](#collection-examples)

## Getting started
Collections can be used with the static `collection()`-method to manipulate the database.
**collection()** takes either a String with the classname, case sensitive, or the Class itself.
This will create a database-file in your project. Easy to deploy or share. 

```java
import static nosqlite.Database.collection;

// creates a database-file in /db-folder called 'data.db'
User john = new User("John");
// generates an UUID
collection("User").save(john); 

User jane = collection("User").findById("lic4XCz2kxSOn4vr0D8BV");

jane.setAge(30);
// updates document with same UUID
collection("User").save(jane); 

// delete Jane
collection("User").deleteById("lic4XCz2kxSOn4vr0D8BV"); 

List<User> users = collection("User").find();
List<User> users = collection(User.class).find();

List<User> usersNamedJohn = collection("User").find(eq("name", "John"));

// or with the statement syntax
List<User> usersNamedJohn = collection("User").find("name==John"); 
```

Watch a collection on changes
```java
// watchData has 3 fields. 
// model - is the document class that was triggered 
// event - is the event triggered - 'insert', 'update' or 'delete'
// data - is a list with effected documents
collection("User").watch(watchData -> {
    List<User> effectedUsers = (List<User>) watchData.data;

    switch(watchData.event) {
        case "insert": // on created document
        break;

        case "update": // on updated document
        break;

        case "delete": // on deleted document
        break;
    }
});
```

### CollectionOptions
CollectionOptions can be passed when enabling collections to set certain options.
Options available are:
- *dbPath* - The default path is "db/data.db". You can override that with this option. 
- *useWatcher* - Enable WebSocket listener on collection changes
- *useBrowser* - Enable collection browser (good when developing)

Note: options must be called before any other call with collection()! 

You can pass one or multiple options when enabling collections:
```java
// default options 
collection(option -> {
    option.dbPath = "db/data.db";
    option.useWatcher = false;
    option.useBrowser = false; 
});
```

**useWatcher**

This starts an `WebSocket` endpoint in the database that will send *watchData* when a change happens.

**WatchData** is an object containing *model*, *event*, *data*.
- *model*: The collection that were triggered
- *event*: The event that was triggered, 'insert', 'update' or 'delete'
- *data*: List of items that are related to the change

To listen to these events on the client you have to create a connection to `'ws://<hostname>:<port>/watch-collections'` with `WebSocket`.

```js
let ws = new WebSocket('ws://localhost:4000/watch-collections')
```

With the webSocket you can listen to messages from the collection channel.

```js
ws.onmessage = messageEvent => {
    const watchData = JSON.parse(messageEvent.data);

    // deconstruct document, event and data from watchData
    const { document, event, data } = watchData;

    if(document == 'BlogPost') {
        // do something with BlogPost
    } 
    else if(document == 'Message') {
        // do something with Message
    }
};
```

#### Example

Java:
```java
collection(option -> {
    option.useWatcher = true;
}); 
```

JavaScript:
```js
ws.onmessage = messageEvent => {
    const watchData = JSON.parse(messageEvent.data);

    // deconstruct model, event and data from watchData
    const { model, event, data } = watchData;

    switch(event) {
        case 'insert':
            // add post to list
            model == 'BlogPost' && posts.push(data[0]);
            model == 'Message' && // add message to list
        break;
        case 'update':
        break;
        case 'delete':
            // remove post from list
            model == 'BlogPost' && (posts = posts.filter(post => post.id !== data[0].id));
        break;
    };

    // update 
    renderPosts();
};
```

**useBrowser**

This will enable the collection browser. This lets you peek at the stored data while developing. 
It might be a good idea to disable this when deploying to save CPU and RAM.

```java
collection(option -> {
    option.useBrowser = true; 
});
```

## Import
The collection supports mocking data as JSON, from example [mockaroo.com](https://www.mockaroo.com/).
**Note**: Format must be a JSON-array.

Simply select a .json-file and click `import`. This will append the data to the collection.
It's important that the fields match the **document** in the collection.

## Export
Export will download the current collection as a .json-file.

This file can easily be used to import into the collection, and can serve as a backup.

The .json-file is also created in the db-directory with the name of the document.

## Drop
Will delete all data in the collection.

### Important note!
Changing the name of a field will not corrupt the database, but will temporarily remove the value from all documents.
Simply revert the name and the value gets restored. 

## Document
Collections can be used as a simple Key/Value store, but it's true potential is when using it with POJOs. (Plain Old Java Objects)
When using POJOs the following two annotations must be present in the class.

### @Document Annotation
Marks a class to be used with a collection. Is required if an object is going to be saved to the collection.

### @Id Annotation
Each object in a Collection must be uniquely identified by a field marked with **@Id** annotation. The collection maintains an unique index on that field to identify the objects.
If no id is manually set, the Collection will generate an UUID to that field when inserted or saved.

```java
import nosqlite.annotations.Document;
import nosqlite.annotations.Id;

@Document
public class MyType {

    @Id
    private String id;
    private String name;
}
```

## Collection methods

To use the collection you need to add which document to query for in the collection parameter, ex `collection("User")` will only query for Users.

**Table 1. Collection methods**

| Operation | Method | Description |
| --- | --- | --- |
| Get all documents | find(Filter) | Returns a list with objects. If no filter is used find() will return ALL documents. |
| Get one document | findOne(Filter) | Returns first found document. |
| Get document with id | findById(String) | Returns the object with mathing id. |
| Create or Update a document | save(Object) | Creates a new document in the collection if no id is present. If theres an id save() will update the existing document in the collection. Can save an array of documents. |
| Update documents | update(Filter, Object) | Update all documents matching the filter. |
| Update a document with id | updateById(String) | Updates the document with matching id. |
| Delete a document | delete(Document) | Deletes the document with matching id. |
| Delete documents | delete(Filter) | Deletes all documents matching the filter. |
| Delete a document with id | deleteById(String) | Deletes the document with matching id. |
| Watch a collection | watch(lambda) | Register a watcher that triggers on changes in the collection. |
| Watch a collection on an event | watch(event, lambda) | Register a watcher that triggers on changes at target event in the collection. |


### Filters

Filters are the selectors in the collection’s find operation. It matches documents in the collection depending on the criteria provided and returns a list of objects.

Make sure you import the static method **Filter**.

```java
import static nosqlite.utilities.Filter.*;
```

**Table 2. Comparison Filter**

| Filter | Method | Description |
| --- | --- | --- |
| Equals | eq(String, Object) | Matches values that are equal to a specified value. |
| Greater | gt(String, Object) | Matches values that are greater than a specified value. |
| GreaterEquals | gte(String, Object) | Matches values that are greater than or equal to a specified value. |
| Lesser | lt(String, Object) | Matches values that are less than a specified value. |
| LesserEquals | lte(String, Object) | Matches values that are less than or equal to a specified value. |
| In | in(String, Object[]) | Matches any of the values specified in an array. |
| NotIn | notIn(String, Object[]) | Matches none of the values specified in an array. |

**Table 3. Logical Filters**

| Filter | Method | Description |
| --- | --- | --- |
| Not | not(Filter) | Inverts the effect of a filter and returns results that do not match the filter. |
| Or | or(Filter[]) | Joins filters with a logical OR returns all ids of the documents that match the conditions of either filter. |
| And | and(Filter[]) | Joins filters with a logical AND returns all ids of the documents that match the conditions of both filters. |

**Table 4. Array Filter**

| Filter | Method | Description |
| --- | --- | --- |
| Element Match | elemMatch(String, Filter) | Matches documents that contain an array field with at least one element that matches the specified filter. |

**Table 5. Text Filters**
*Note*: For these filters to work the field must be indexed. See [Annotations](#annotations)

| Filter | Method | Description |
| --- | --- | --- |
| Text | text(String, String) | Performs full-text search. |
| Regex | regex(String, String) | Selects documents where values match a specified regular expression. |

### FindOptions

A FindOptions is used to specify search options. It provides pagination as well as sorting mechanism.

```java
import static org.dizitart.no2.FindOptions.*;
```

Example
```java
// sorts all documents by age in ascending order then take first 10 documents and return as a List
List<User> users = collection("User").find(sort("age", SortOrder.Ascending).thenLimit(0, 10));
```
```java
// sorts the documents by age in ascending order
List<User> users = collection("User").find(sort("age", SortOrder.Ascending));
```
```java
// sorts the documents by name in ascending order with custom collator
List<User> users = collection("User").find(sort("name", SortOrder.Ascending, Collator.getInstance(Locale.FRANCE)));
```
```java
// fetch 10 documents starting from offset = 2
List<User> users = collection("User").find(limit(2, 10));
```

## Collection Examples

**and()**
```java
// matches all documents where 'age' field has value as 30 and
// 'name' field has value as John Doe
collection("User").find(and(eq("age", 30), eq("name", "John Doe")));
```

**or()**
```java
// matches all documents where 'age' field has value as 30 or
// 'name' field has value as John Doe
collection("User").find(or(eq("age", 30), eq("name", "John Doe")));
```

**not()**
```java
// matches all documents where 'age' field has value not equals to 30
collection("User").find(not(eq("age", 30)));
```

**eq()**
```java
// matches all documents where 'age' field has value as 30
collection("User").find(eq("age", 30));
```

**gt()**
```java
// matches all documents where 'age' field has value greater than 30
collection("User").find(gt("age", 30));
```

**gte()**
```java
// matches all documents where 'age' field has value greater than or equal to 30
collection("User").find(gte("age", 30));
```

**lt()**
```java
// matches all documents where 'age' field has value less than 30
collection("User").find(lt("age", 30));
```

**lte()**
```java
// matches all documents where 'age' field has value lesser than or equal to 30
collection("User").find(lte("age", 30));
```

**in()**
```java
// matches all documents where 'age' field has value in [20, 30, 40]
collection("User").find(in("age", 20, 30, 40));
```

**notIn()**
```java
// matches all documents where 'age' field does not have value in [20, 30, 40]
collection("User").find(notIn("age", 20, 30, 40));
```

**elemMatch()**
```java
// matches all documents which has an array field - 'color' and the array
// contains a value - 'red'.
collection("User").find(elemMatch("color", eq("$", "red"));
```

**text()**
```java
// matches all documents where 'address' field has a word 'roads'.
collection("User").find(text("address", "roads"));

// matches all documents where 'address' field has word that starts with '11A'.
collection("User").find(text("address", "11a*"));

// matches all documents where 'address' field has a word that ends with 'Road'.
collection("User").find(text("address", "*road"));

// matches all documents where 'address' field has a word that contains a text 'oa'.
collection("User").find(text("address", "*oa*"));

// matches all documents where 'address' field has words like '11a' and 'road'.
collection("User").find(text("address", "11a road"));

// matches all documents where 'address' field has word 'road' and another word that start with '11a'.
collection("User").find(text("address", "11a* road"));
```

**regex()**
```java
// matches all documents where 'name' value starts with 'jim' or 'joe'.
collection("User").find(regex("name", "^(jim|joe).*"));
```
