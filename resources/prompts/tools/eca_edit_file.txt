You must use your `eca_read_file` tool to get the file's exact contents before attempting an edit.
This tool will error if you attempt an edit without reading the file.When crafting the `orginal_content`, you must match the original content from the `eca_read_file` tool output exactly, including all indentation (spaces/tabs) and newlines.
Never include any part of the line number prefix in the `original_content` or `new_content`.The edit will FAIL if the `original_content` is not unique in the file. To resolve this, you must expand the `new_content` to include more surrounding lines of code or context to make it a unique block.
ALWAYS prefer making small, targeted edits to existing files. Avoid replacing entire functions or large blocks of code in a single step unless absolutely necessary.
To delete content, provide the content to be removed as the `original_content` and an empty string as the `new_content`.
To prepend or append content, the `new_content` must contain both the new content and the original content from `old_string`.
