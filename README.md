# NoSQLite
A single file NoSQL database utilizing SQLite JSON1 extension.

It's a lightweight embedded document database, ideal for small web applications.

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
- [Installation](#installation)
- [Getting started](#getting-started)
- [CollectionConfig](#collectionconfig)
  - [Watcher](#watcher)
  - [Browser](#browser)
- [Document](#document)
- [Collection methods](#collection-methods)
  - [Filters](#filters)
  - [FindOptions](#findoptions)
- [Collection Examples](#collection-examples)
- [Filter nested objects](#filter-nested-objects)
- [Import](#import)
- [Export](#export)
- [Drop](#drop)
  - [Important note](#important-note)

## Installation
### Download
> Direct download as jar:

[nosqlite-1.0.0.jar](https://github.com/Aarkan1/nosqlite/releases/download/1.0.0/nosqlite-1.0.0.jar)

### Maven
> Add repository:

```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>
```

> Add dependency:

```xml
<dependency>
    <groupId>com.github.Aarkan1</groupId>
    <artifactId>nosqlite</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle
> Add this to your build.gradle

```golang
repositories {
    maven { url "https://jitpack.io/" }
}

dependencies {
    compile 'com.github.Aarkan1:nosqlite:1.0.0'
}
```

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

### CollectionConfig
CollectionConfig can be passed when enabling collections to set certain options.
Options available are:
- *dbPath* - The default path is "db/data.db". You can override that with this option. 
- *runAsync* - Enables threaded async calls to the database.
- *useWatcher* - Enable WebSocket listener on collection changes. With *runAsync* this triggers on a different thread.
- *useBrowser* - Enable collection browser (good when developing)

**Note:** options must be called before any other call with collection()! 

You can pass one or multiple options when enabling collections:
```java
// default options 
collection(option -> {
    option.dbPath = "db/data.db";
    option.runAsync = true; 
    option.useWatcher = false;
    option.useBrowser = false; 
});
```

#### Watcher

**useWatcher** starts an `WebSocket` endpoint in the database that will send *watchData* when a change happens.

**WatchData** is an object containing *model*, *event*, *data*.
- *model*: The collection that were triggered
- *event*: The event that was triggered, either 'insert', 'update' or 'delete'
- *data*: List of documents that are related to the change

To listen to these events on the client you have to create a WebSocket connection to `'ws://<hostname>:<port>/watch-collections'`.

```js
let ws = new WebSocket('ws://localhost:4000/watch-collections')
```

With the webSocket you can listen to messages from the collection channel.

```js
ws.onmessage = messageEvent => {
    const watchData = JSON.parse(messageEvent.data);

    // deconstruct model, event and data from watchData
    const { model, event, data } = watchData;

    if(model == 'BlogPost') {
        // do something with BlogPost
    } 
    else if(model == 'Message') {
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

#### Browser

**useBrowser** will enable the collection browser. This lets you peek at the stored data while developing. 
It might be a good idea to disable this when deploying to save CPU and RAM.

```java
collection(option -> {
    option.useBrowser = true; 
});
```

## Document
Collections can be used as a simple key/value store, but it's true potential is when using it with POJOs. (Plain Old Java Objects)
When using POJOs the following two annotations must be present in the class.

### @Document Annotation
Marks a class to be used with a collection. Is required if an object is going to be saved to the collection.

### @Id Annotation
Each object in a Collection must be uniquely identified by a String field marked with **@Id** annotation. The collection maintains an unique index on that field to identify the objects.
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
Data is stored in the collection as JSON, and the `find()`-methods parse this JSON to target class.
`findAsJson()`-methods is MUCH MUCH faster, because no parsing is required. This is good when only sending data from a collection directly over the network.

**Table 1. Collection methods**

| Operation | Method | Description |
| --- | --- | --- |
| Get all documents | find(Filter) | Returns a list with objects. If no filter is used find() will return ALL documents. |
| Get one document | findOne(Filter) | Returns first found document. |
| Get document with id | findById(id) | Returns the object with matching id. |
| Get all documents as JSON | findAsJson(Filter) | Returns a list with objects as JSON. If no filter is used find() will return ALL documents. |
| Get one document as JSON | findOneAsJson(Filter) | Returns first found document as JSON. |
| Get document with id as JSON | findByIdAsJson(id) | Returns the object with matching id as JSON. |
| Create or Update a document | save(Object) | Creates a new document in the collection if no id is present. If theres an id save() will update the existing document in the collection. Can save an array of documents. |
| Update documents | updateField(fieldName, newValue) | Update all documents fields with new value. |
| Update a document field with Object | updateField(Object, fieldName, newValue) | Updates the document field with matching id. |
| Update a document field with id | updateFieldById(id, fieldName, newValue) | Updates the document field with matching id. |
| Update documents | changeFieldName(newFieldName, oldFieldName) | Change field name on all documents. |
| Update documents | removeField(fieldName) | Removes field from all documents. |
| Delete a document | delete(Document) | Deletes the document with matching id. |
| Delete documents | delete(Filter) | Deletes all documents matching the filter. |
| Delete a document with id | deleteById(id) | Deletes the document with matching id. |
| Get number of documents | count() | Returns the count of all documents in a collection. |
| Watch a collection | watch(lambda) | Register a watcher that triggers on changes in the collection. |
| Watch a collection on an event | watch(event, lambda) | Register a watcher that triggers on changes at target event in the collection. |

**Table 1.2. Collection as a key/value store methods**

When using the collection as a key/value store you can name the collection anything you want.
```java
collection("pets").put("snuggles", new Cat("Snuggles"));
collection("pets").put("pluto", new Dog("Pluto"));

Dog pluto = collection("pets").get("pluto", Dog.class);
```

| Operation | Method | Description |
| --- | --- | --- |
| Get value by key | get(key) | Returns an object as JSON. |
| Get value by key as a POJO | get(key, class) | Returns an object parsed to target class. |
| Store object at key | put(key, value) | Stores the value as JSON at target key. Replaces value if key exists. |
| Store object at key | putIfAbsent(key, value) | Stores the value as JSON at target key. Does not replace value if key exists. |
| Remove value by key | remove(key) | Removes both key and value. |

### Filters

Filter are the selectors in the collection’s find operation. It matches documents in the collection depending on the criteria provided and returns a list of objects.

Make sure you import the static method **Filter**.

```java
import static nosqlite.utilities.Filter.*;
```

**Table 2. Comparison Filter**

| Filter | Method | Description |
| --- | --- | --- |
| Equals | eq(String, Object) | Matches values that are equal to a specified value. |
| NotEquals | ne(String, Object) | Matches values that are not equal to a specified value. |
| Greater | gt(String, Object) | Matches values that are greater than a specified value. |
| GreaterEquals | gte(String, Object) | Matches values that are greater than or equal to a specified value. |
| Lesser | lt(String, Object) | Matches values that are less than a specified value. |
| LesserEquals | lte(String, Object) | Matches values that are less than or equal to a specified value. |
| In | in(String, Object[]) | Matches any of the values specified in an array. |

**Table 3. Logical Filters**

| Filter | Method | Description |
| --- | --- | --- |
| Not | not(Filter) | Inverts the effect of a filter and returns results that do not match the filter. |
| Or | or(Filter...) | Joins filters with a logical OR returns all ids of the documents that match the conditions of either filter. |
| And | and(Filter...) | Joins filters with a logical AND returns all ids of the documents that match the conditions of both filters. |

**Table 4. Text Filters**

| Filter | Method | Description |
| --- | --- | --- |
| Text | text(String, String) | Performs full-text search. Same rules as [SQL LIKE](https://www.w3schools.com/sql/sql_like.asp) |
| Regex | regex(String, String) | Selects documents where values match a specified regular expression. |

### FindOptions

A FindOptions is used to specify search options. It provides pagination as well as sorting mechanism.
The config syntax with lambda is more clear and easier to read.

Example
```java
// sorts all documents by age in ascending order then take first 10 documents and return as a List
List<User> users = collection("User").find(null, "age=asc", 10, 0);

// or with FindOptions
List<User> users = collection("User").find(op -> {
        op.sort = "age=asc";
        op.limit = 10;
    });
```
```java
// sorts the documents by age in ascending order
List<User> users = collection("User").find(null, "age=asc", 0, 0);

// or with FindOptions
List<User> users = collection("User").find(op -> {
        op.filter = "age=asc";
    });
```
```java
// fetch 10 documents starting from offset = 2
List<User> users = collection("User").find(10, 2);

// or with FindOptions
List<User> users = collection("User").find(op -> {
        op.limit = 10;
        op.offset = 2;
    });
```

## Collection Examples

**and()**
```java
// matches all documents where 'age' field has value as 30 and
// 'name' field has value as John Doe
collection("User").find(and(eq("age", 30), eq("name", "John Doe")));
// with the statement syntax
collection("User").find("age==30 && name==John Doe");
```

**or()**
```java
// matches all documents where 'age' field has value as 30 or
// 'name' field has value as John Doe
collection("User").find(or(eq("age", 30), eq("name", "John Doe")));
// with the statement syntax
collection("User").find("age==30 || name==John Doe");
```

**not()**
```java
// matches all documents where 'age' field has value not equals to 30
// and name is not John Doe
collection("User").find(not(and((eq("age", 30), eq("name", "John Doe"))));
// with the statement syntax
collection("User").find("!(age==30 && name==John Doe)");
```

**eq()**
```java
// matches all documents where 'age' field has value as 30
collection("User").find(eq("age", 30));
// with the statement syntax
collection("User").find("age==30");
```

**ne()**
```java
// matches all documents where 'age' field has value not equals to 30
collection("User").find(ne("age", 30));
// with the statement syntax
collection("User").find("age!=30");
```

**gt()**
```java
// matches all documents where 'age' field has value greater than 30
collection("User").find(gt("age", 30));
// with the statement syntax
collection("User").find("age>30");
```

**gte()**
```java
// matches all documents where 'age' field has value greater than or equal to 30
collection("User").find(gte("age", 30));
// with the statement syntax
collection("User").find("age>=30");
```

**lt()**
```java
// matches all documents where 'age' field has value less than 30
collection("User").find(lt("age", 30));
// with the statement syntax
collection("User").find("age<30");
```

**lte()**
```java
// matches all documents where 'age' field has value lesser than or equal to 30
collection("User").find(lte("age", 30));
// with the statement syntax
collection("User").find("age<=30");
```

**in()**
```java
// matches all documents where 'age' field has value in [20, 30, 40]
collection("User").find(in("age", 20, 30, 40));

List ages = List.of(20, 30, 40);
collection("User").find(in("age", ages));

// with the statement syntax
collection("User").find("age==[20, 30, 40]");
```


**text()**
Same rules as [SQL LIKE](https://www.w3schools.com/sql/sql_like.asp)
* The percent sign (%) represents zero, one, or multiple characters
* The underscore sign (_) represents one, single character

```java
// matches all documents where 'address' field start with "a"
collection("User").find(text("address", "a%"));

// with the statement syntax, applies to all text() examples
collection("User").find("address=~a%");

// matches all documents where 'address' field end with "a"
collection("User").find(text("address", "%a"));

// matches all documents where 'address' field have "or" in any position
collection("User").find(text("address", "%or%"));

// matches all documents where 'address' field have "r" in the second position
collection("User").find(text("address", "_r%"));

// matches all documents where 'address' field start with "a" and are at least 2 characters in length
collection("User").find(text("address", "a_%"));

// matches all documents where 'address' field start with "a" and are at least 3 characters in length
collection("User").find(text("address", "'a__%"));

// matches all documents where 'address' field start with "a" and ends with "o"
collection("User").find(text("address", "a%o"));
```

**regex()**
```java
// matches all documents where 'name' value starts with 'jim' or 'joe'.
collection("User").find(regex("name", "^(jim|joe).*"));
// with the statement syntax
collection("User").find("name~~^(jim|joe).*");
```

## Filter nested objects

It's just as easy to filter nested objects in a collection. Each nested property is accessible with a dot-filter for each level.

```java
// matches all documents where a User's cat has an age of 7
collection("User").find(eq("cat.age", 7));
// with the statement syntax
collection("User").find("cat.age==7");

// matches all documents where a User's headphone has a brand of Bose
collection("User").find(eq("accessory.headphones.brand", "Bose"));
// with the statement syntax
collection("User").find("accessory.headphones.brand==Bose");
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
You can manage fields with [collection methods](#collection-methods).
