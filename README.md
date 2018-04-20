# irulan

Define specs for events, commands, and other basic building blocks of
an event sourcing system.

## Usage

A number of useful specs for common, reusable, non-domain-specific
elements are in the `org.purefn.irulan.common` namespace. This includes

- `common/base64?`
- `common/ip4-adddr?`
- `common/fqdn?`
- `common/host?`
- `common/timestamp?`

### Events

Irulan defines events in terms of two required elements. A provenance,
which describes where the event came from, and a payload, which has
the contents of the event. The payload varies based on the event type.

Provenance includes the event's id (a guid), its creation time, and
information about the process that created the event, along with
optional event parents.

The payload requires a type key which defines the type of the event,
along with type-specific data.

Events are versioned to allow for evolution over time. We define an
event using Irulan's `defevent` macro.

`defevent` has the following signature:

    (defevent name docstring? event-type version*)`

`name` is a symbol that will be `def`'d in the namespace that
`defevent` is called from, and will be bound to a constructor for the
event.

`docstring?` is an optional docstring describing the event, which will
be placed on the constructor function as well as in the event
registry.

`event-type` is a namespaced keyword for the event itself. It should
describe the domain of the event. It will be added to Irulan's event
registry as well as the spec registry with an appropriate generator.

One or more event versions is required. Each version is defined in the
following way:

    (version n docstring? spec :up upcast-fn :down downcast-fn)

Each version declaration begins with the symbol `version` and a
non-negative integer version number. An optional docstring can be
supplied. `spec` should be a spec for the payload of this particular
event version. No validation on the event type field needs to be
provided in this spec.

The upcast and downcast functions should not be supplied for the first
version, but are required for every subsequent version. They are
transformation functions in terms of the previous version: upcast to
take an event from the previous version and cast it to the given
version, and downcast to take an event from the current version and
cast it to the previous version. In this way, every event can be
transformed to any version.

To define an event:

```clojure
(require '[org.purefn.irulan.event :as event]
         '[clojure.spec :as s])

(s/def ::url string?)
(s/def ::useragent string?)
(s/def ::email-id string?)

(event/defevent clicked
  "Link in an email was clicked."
  :email.sendwithus/clicked
  (version 1 (s/keys :req-un [:url :email-id]))
  (version 2 (s/keys :req-un [:url :email-id :useragent])
    :up #(update % :useragent (fn [ua] (or ua "")))
    :down identity))
```

## License

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
