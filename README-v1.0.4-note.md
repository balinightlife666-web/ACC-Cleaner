# ACC Cleaner v1.0.4 fix note

- Deep Scan preview now falls back to a secure FileProvider URI when a file is not indexed by MediaStore.
- This specifically covers real files found in locations such as `.trash-storage` that previously appeared in review but could not be opened.
- No auto-select and no auto-delete behavior remains unchanged.
