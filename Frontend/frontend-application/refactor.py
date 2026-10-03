import os
import re
import sys

def get_relative_path(from_path, to_path):
    from_dir = os.path.dirname(from_path)
    rel = os.path.relpath(to_path, from_dir)
    if not rel.startswith('.'):
        rel = './' + rel
    return rel

def main():
    root = "src/app"
    
    # Path mappings (old -> new)
    moves = [
        ("core/admin", "features/admin"),
        ("core/auth/register", "features/auth/register"),
        ("core/auth/sign-in", "features/auth/sign-in"),
        ("core/dashboard", "features/dashboard"),
        ("core/trade", "features/trade")
    ]
    
    # Execute the moves
    os.makedirs(os.path.join(root, "features/auth"), exist_ok=True)
    for old, new in moves:
        if os.path.exists(os.path.join(root, old)):
            os.system(f"mv {os.path.join(root, old)} {os.path.join(root, new)}")

    # Update imports in all ts files
    # We will just rely on the Angular compiler to tell us what is wrong, 
    # but let's try to update the obvious ones.
    
    # Actually, writing a perfect import rewriter in python is tricky without AST.
    # Let's do a simple regex for `from '...'` and replace old paths.

main()
